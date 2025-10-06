package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageType;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.messages.Message.MessageBuilder;
import com.fincity.saas.message.model.message.whatsapp.messages.ReadMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.TextMessage;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.message.whatsapp.webhook.IChange;
import com.fincity.saas.message.model.message.whatsapp.webhook.IContact;
import com.fincity.saas.message.model.message.whatsapp.webhook.IEntry;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMessage;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMetadata;
import com.fincity.saas.message.model.message.whatsapp.webhook.IStatus;
import com.fincity.saas.message.model.message.whatsapp.webhook.IValue;
import com.fincity.saas.message.model.message.whatsapp.webhook.IWebHookEvent;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMediaRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageCswRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappReadRequest;
import com.fincity.saas.message.model.response.MessageResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.util.PhoneUtil;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class WhatsappMessageService
        extends AbstractMessageService<MessageWhatsappMessagesRecord, WhatsappMessage, WhatsappMessageDAO> {

    public static final String WHATSAPP_PROVIDER_URI = "/whatsapp";
    private static final String WHATSAPP_MESSAGE_CACHE = "whatsappMessage";
    private static final String SUBSCRIBE = "subscribe";
    private static final String WHATSAPP_CLOUD_MESSAGE_LOCATION = "/whatsapp/cloud/message";
    private final WhatsappApiFactory whatsappApiFactory;
    private final WhatsappPhoneNumberService whatsappPhoneNumberService;
    private final WhatsappCswService customerServiceWindowService;

    private WhatsappBusinessAccountService businessAccountService;

    @Autowired
    public WhatsappMessageService(
            WhatsappApiFactory whatsappApiFactory,
            WhatsappPhoneNumberService whatsappPhoneNumberService,
            WhatsappCswService customerServiceWindowService) {
        this.whatsappApiFactory = whatsappApiFactory;
        this.whatsappPhoneNumberService = whatsappPhoneNumberService;
        this.customerServiceWindowService = customerServiceWindowService;
    }

    @Autowired
    public void setBusinessAccountService(WhatsappBusinessAccountService businessAccountService) {
        this.businessAccountService = businessAccountService;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_MESSAGE_CACHE;
    }

    @Override
    protected Mono<WhatsappMessage> updatableEntity(WhatsappMessage entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setMessageStatus(entity.getMessageStatus());
            existing.setSentTime(entity.getSentTime());
            existing.setDeliveredTime(entity.getDeliveredTime());
            existing.setReadTime(entity.getReadTime());
            existing.setFailedTime(entity.getFailedTime());
            existing.setFailureReason(entity.getFailureReason());

            existing.setMessage(entity.getMessage());
            existing.setInMessage(entity.getInMessage());
            existing.setMessageResponse(entity.getMessageResponse());
            existing.setMediaFileDetail(entity.getMediaFileDetail());

            return Mono.just(existing);
        });
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.WHATSAPP_MESSAGE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    public String getProviderUri() {
        return WHATSAPP_PROVIDER_URI;
    }

    @Override
    public Mono<Message> toMessage(WhatsappMessage providerObject) {
        return Mono.just(new Message()
                        .setUserId(providerObject.getUserId())
                        .setMessageProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(providerObject.isOutbound())
                        .setWhatsappMessageId(providerObject.getId() != null ? providerObject.getId() : null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.toMessage"));
    }

    @Override
    public Mono<Message> sendMessage(MessageAccess access, MessageRequest messageRequest, Connection connection) {

        if (!messageRequest.isValid()) return super.throwMissingParam(MessageRequest.Fields.text);

        if (messageRequest.isConnectionNull()) return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        WhatsappMessage whatsappMessage = WhatsappMessage.ofOutbound(
                MessageBuilder.builder()
                        .setTo(messageRequest.getToNumber().getNumber())
                        .buildTextMessage(new TextMessage().setBody(messageRequest.getText())),
                PhoneUtil.parse(access.getUser().getPhoneNumber()),
                null);

        return this.sendMessageInternal(access, connection, null, whatsappMessage);
    }

    public Mono<Message> sendMessage(WhatsappMessageRequest whatsappMessageRequest) {

        if (whatsappMessageRequest.isConnectionNull())
            return this.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        if (!whatsappMessageRequest.isValid()) return super.throwMissingParam(WhatsappMessageRequest.Fields.message);

        if (whatsappMessageRequest.isConnectionNull())
            return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        if (whatsappMessageRequest.getMessage().getType().isMediaFile()
                && (whatsappMessageRequest.getFileDetail() == null
                        || whatsappMessageRequest.getFileDetail().isEmpty()))
            return super.throwMissingParam(WhatsappMessage.Fields.mediaFileDetail);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), whatsappMessageRequest.getConnectionName()),
                (access, connection) -> this.sendMessageInternal(
                        access,
                        connection,
                        whatsappMessageRequest.getWhatsappPhoneNumberId(),
                        WhatsappMessage.ofOutbound(
                                whatsappMessageRequest.getMessage(),
                                PhoneUtil.parse(access.getUser().getPhoneNumber()),
                                whatsappMessageRequest.getFileDetail())));
    }

    private Mono<Message> sendMessageInternal(
            MessageAccess access,
            Connection connection,
            Identity whatsappPhoneNumberId,
            WhatsappMessage whatsappMessage) {

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.getWhatsappBusinessAccount(access, connection),
                        (vConn, businessAccount) ->
                                this.getWhatsappPhoneNumber(whatsappPhoneNumberId, access, businessAccount.getId()),
                        (vConn, businessAccount, phoneNumberId) ->
                                this.validateCustomerServiceWindow(access, phoneNumberId, whatsappMessage),
                        (vConn, businessAccount, phoneNumberId, validated) ->
                                this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (vConn, businessAccount, phoneNumberId, validated, api) ->
                                api.sendMessage(phoneNumberId.getPhoneNumberId(), whatsappMessage.getMessage()),
                        (vConn, businessAccount, phoneNumberId, validated, api, response) -> this.createInternal(
                                access,
                                whatsappMessage.update(businessAccount.getId(), phoneNumberId.getId(), response)),
                        (vConn, businessAccount, phoneNumberId, validated, api, response, created) ->
                                this.toMessage(created).map(msg -> msg.setConnectionName(connection.getName())),
                        (vConn, businessAccount, phoneNumberId, validated, api, response, created, msg) ->
                                super.messageService
                                        .createInternal(access, msg)
                                        .flatMap(msgCreated -> this.messageEventService
                                                .sendMessageEvent(access, created)
                                                .thenReturn(msgCreated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.sendMessageInternal"));
    }

    public Mono<String> verifyMetaWebhook(String mode, String token, String challenge) {

        logger.info("Received webhook verification request: mode={}, token={}, challenge={}", mode, token, challenge);

        return SUBSCRIBE.equals(mode) && verifyToken.equals(token) ? Mono.just(challenge) : Mono.empty();
    }

    private Mono<WhatsappBusinessAccount> getWhatsappBusinessAccount(MessageAccess access, Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return this.businessAccountService.getBusinessAccount(access, businessAccountId);
    }

    private Mono<WhatsappPhoneNumber> getWhatsappPhoneNumber(
            Identity whatsappPhoneNumberId, MessageAccess access, ULong businessAccountId) {
        if (whatsappPhoneNumberId != null && !whatsappPhoneNumberId.isNull())
            return whatsappPhoneNumberService
                    .readIdentityWithAccessEmpty(access, whatsappPhoneNumberId)
                    .switchIfEmpty(this.getAccountWhatsappPhoneNumber(access, businessAccountId));

        return this.getAccountWhatsappPhoneNumber(access, businessAccountId);
    }

    private Mono<WhatsappPhoneNumber> getAccountWhatsappPhoneNumber(MessageAccess access, ULong businessAccountId) {
        return whatsappPhoneNumberService
                .getByAccountId(access, businessAccountId)
                .switchIfEmpty(super.throwMissingParam(WhatsappMessage.Fields.whatsappPhoneNumberId));
    }

    public Mono<MessageResponse> processWebhookEvent(String appCode, String clientCode, IWebHookEvent event) {

        if (clientCode.equals("SYSTEM"))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.WEBHOOK_CONFIG_NOT_DONE);

        if (event == null || event.getEntry() == null) return Mono.empty();

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);

        return super.messageWebhookService
                .createWhatsappWebhookEvent(access, event)
                .flatMap(wEvent -> Flux.fromIterable(event.getEntry())
                        .flatMap(entry -> this.processEntry(access, entry))
                        .then()
                        .then(super.messageWebhookService.processed(wEvent))
                        .onErrorResume(error -> {
                            logger.error(
                                    "Error processing Whatsapp webhook event for app: {}, client: {}",
                                    appCode,
                                    clientCode,
                                    error);
                            return Mono.just(MessageResponse.ofBadRequest(
                                    wEvent.getCode(),
                                    super.messageWebhookService.getMessageSeries(),
                                    error.getMessage()));
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.processWebhookEvent"));
    }

    private Mono<Void> processEntry(MessageAccess access, IEntry entry) {
        if (entry.getChanges() == null) return Mono.empty();

        return Flux.fromIterable(entry.getChanges())
                .flatMap(change -> this.processChange(access, change))
                .then();
    }

    private Mono<Void> processChange(MessageAccess access, IChange change) {
        if (change.getValue() == null) return Mono.empty();

        IValue value = change.getValue();

        if (value.getMessages() != null && !value.getMessages().isEmpty()) {
            return Flux.fromIterable(value.getMessages())
                    .flatMap(message -> processIncomingMessage(
                            access,
                            message,
                            value.getMetadata(),
                            value.getContacts().getFirst()))
                    .then();
        } else if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
            return this.processStatusUpdates(access, value.getStatuses());
        }

        return Mono.empty();
    }

    private Mono<Message> processIncomingMessage(
            MessageAccess access, IMessage iMessage, IMetadata metadata, IContact contact) {

        logger.info("Processing incoming message: {} from {}", iMessage.getId(), iMessage.getFrom());

        String phoneNumberId = metadata != null ? metadata.getPhoneNumberId() : null;
        if (phoneNumberId == null) {
            logger.error("Phone number ID is null for incoming message: {}", iMessage.getId());
            return Mono.empty();
        }

        return FlatMapUtil.flatMapMono(
                        () -> this.whatsappPhoneNumberService.getByPhoneNumberId(access, phoneNumberId),
                        whatsappPhoneNumber -> this.dao
                                .findByUniqueField(iMessage.getId())
                                .flatMap(existing -> this.updateExistingMessage(
                                        access.setUserId(whatsappPhoneNumber.getUserId()),
                                        existing,
                                        metadata,
                                        contact,
                                        iMessage,
                                        whatsappPhoneNumber.getWhatsappBusinessAccountId(),
                                        whatsappPhoneNumber.getId()))
                                .switchIfEmpty(this.createInternal(
                                        access.setUserId(whatsappPhoneNumber.getUserId()),
                                        WhatsappMessage.ofInbound(
                                                metadata,
                                                contact,
                                                iMessage,
                                                whatsappPhoneNumber.getWhatsappBusinessAccountId(),
                                                whatsappPhoneNumber.getId()))),
                        (whatsappPhoneNumber, whatsappMessage) -> this.toMessage(whatsappMessage),
                        (whatsappPhoneNumber, whatsappMessage, message) -> this.messageService
                                .createInternal(access, message)
                                .flatMap(msgCreated -> this.messageEventService
                                        .sendIncomingMessageEvent(access, whatsappMessage)
                                        .thenReturn(msgCreated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.processIncomingMessage"));
    }

    private Mono<WhatsappMessage> updateExistingMessage(
            MessageAccess access,
            WhatsappMessage existing,
            IMetadata metadata,
            IContact contact,
            IMessage message,
            ULong whatsappBusinessAccountId,
            ULong whatsappPhoneNumberId) {

        PhoneNumber from = PhoneNumber.ofWhatsapp(message.getFrom());
        PhoneNumber to = PhoneNumber.ofWhatsapp(metadata.getDisplayPhoneNumber());

        return super.updateInternalWithoutUser(
                access,
                existing.setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                        .setMessageId(message.getId())
                        .setWhatsappPhoneNumberId(whatsappPhoneNumberId)
                        .setFromDialCode(from.getCountryCode())
                        .setFrom(from.getNumber())
                        .setToDialCode(to.getCountryCode())
                        .setTo(to.getNumber())
                        .setCustomerDialCode(from.getCountryCode())
                        .setCustomerPhoneNumber(from.getNumber())
                        .setCustomerWaId(contact.getWaId())
                        .setMessageType(message.getType())
                        .setMessageStatus(MessageStatus.DELIVERED)
                        .setDeliveredTime(
                                message.getTimestamp() != null
                                        ? LocalDateTime.ofInstant(
                                                Instant.ofEpochSecond(Long.parseLong(message.getTimestamp())),
                                                ZoneOffset.UTC)
                                        : LocalDateTime.now())
                        .setOutbound(Boolean.FALSE)
                        .setInMessage(message));
    }

    private Mono<Void> processStatusUpdates(MessageAccess access, List<IStatus> statuses) {
        logger.info("Processing {} status updates", statuses.size());
        return Flux.fromIterable(statuses)
                .flatMap(status -> this.processStatusUpdate(access, status))
                .then();
    }

    private Mono<Void> processStatusUpdate(MessageAccess access, IStatus status) {
        logger.info("Processing status update for message: {} - status: {}", status.getId(), status.getStatus());

        return this.dao
                .findByUniqueField(status.getId())
                .flatMap(whatsappMessage -> this.updateMessageStatus(access, whatsappMessage, status))
                .doOnSuccess(updated -> {
                    if (updated != null) {
                        logger.info("Updated message status: {} -> {}", status.getId(), status.getStatus());
                    }
                })
                .then()
                .onErrorResume(error -> {
                    logger.error("Failed to update message status for {}: {}", status.getId(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<WhatsappMessage> updateMessageStatus(
            MessageAccess access, WhatsappMessage whatsappMessage, IStatus status) {
        whatsappMessage.setMessageStatus(status.getStatus());

        if (status.getTimestamp() != null) {
            long timestamp = Long.parseLong(status.getTimestamp());
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);

            switch (status.getStatus()) {
                case DELIVERED:
                    whatsappMessage.setDeliveredTime(dateTime);
                    break;
                case READ:
                    if (whatsappMessage.getDeliveredTime() != null) whatsappMessage.setDeliveredTime(dateTime);
                    whatsappMessage.setReadTime(dateTime);
                    break;
                case FAILED:
                    whatsappMessage.setFailedTime(dateTime);
                    if (status.getErrors() != null && !status.getErrors().isEmpty())
                        whatsappMessage.setFailureReason(
                                status.getErrors().getFirst().getTitle());
                    break;
                default:
                    break;
            }
        }

        return super.updateInternalWithoutUser(access, whatsappMessage);
    }

    private Mono<Boolean> validateCustomerServiceWindow(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, WhatsappMessage whatsappMessage) {

        PhoneNumber customerPhone = PhoneNumber.of(whatsappMessage.getToDialCode(), whatsappMessage.getTo());

        boolean isTemplateMessage = whatsappMessage.getMessageType() == MessageType.TEMPLATE;

        return customerServiceWindowService
                .canSendMessage(access, whatsappPhoneNumber, customerPhone, isTemplateMessage)
                .flatMap(canSend -> {
                    if (Boolean.FALSE.equals(canSend))
                        return Mono.error(
                                new GenericException(
                                        HttpStatus.BAD_REQUEST,
                                        "Cannot send non-template message outside customer service window. "
                                                + "Customer service window is open for 24 hours after receiving a message from the customer. "
                                                + "Use template messages to initiate conversations or send messages outside the window."));
                    return Mono.just(Boolean.TRUE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.validateCustomerServiceWindow"));
    }

    public Mono<WhatsappCswService.CswStatus> getCswStatus(WhatsappMessageCswRequest request) {

        if (request.isConnectionNull()) return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.messageConnectionService
                                .getCoreDocument(
                                        access.getAppCode(), access.getClientCode(), request.getConnectionName())
                                .flatMap(super::isValidConnection),
                        this::getWhatsappBusinessAccount,
                        (access, connection, businessAccount) -> this.getWhatsappPhoneNumber(
                                request.getWhatsappPhoneNumberId(), access, businessAccount.getId()),
                        (access, connection, businessAccount, phoneNumber) ->
                                this.customerServiceWindowService.getCustomerServiceWindowStatus(
                                        access, phoneNumber, request.getCustomerNumber()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.getCswStatus"));
    }

    public Mono<Response> markMessageAsRead(WhatsappReadRequest request) {

        if (request.isConnectionNull()) return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.messageConnectionService
                                .getCoreDocument(
                                        access.getAppCode(), access.getClientCode(), request.getConnectionName())
                                .flatMap(super::isValidConnection),
                        this::getWhatsappBusinessAccount,
                        (access, connection, businessAccount) ->
                                this.readIdentityWithAccess(access, request.getMessageId()),
                        (access, connection, businessAccount, message) -> this.getWhatsappPhoneNumber(
                                request.getWhatsappPhoneNumberId(), access, businessAccount.getId()),
                        (access, connection, businessAccount, message, phoneNumber) ->
                                this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (access, connection, businessAccount, message, phoneNumber, api) -> Mono.zip(
                                        api.markMessageAsRead(
                                                phoneNumber.getPhoneNumberId(),
                                                new ReadMessage().setMessageId(message.getMessageId())),
                                        this.markConversationAsRead(access, message))
                                .map(Tuple2::getT1))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.markMessageAsRead"));
    }

    private Mono<Integer> markConversationAsRead(MessageAccess access, WhatsappMessage message) {
        LocalDateTime readTime = LocalDateTime.now(ZoneOffset.UTC);
        return this.dao.markConversationAsRead(
                access,
                message.getWhatsappPhoneNumberId(),
                message.getCustomerPhoneNumber(),
                message.getCustomerDialCode(),
                readTime,
                message.getCreatedAt());
    }

    public Mono<WhatsappMessage> downloadMediaFile(WhatsappMediaRequest request) {

        if (request.isConnectionNull()) return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.messageConnectionService
                                .getCoreDocument(
                                        access.getAppCode(), access.getClientCode(), request.getConnectionName())
                                .flatMap(super::isValidConnection),
                        (access, connection) -> this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (access, connection, api) ->
                                this.readIdentityWithAccess(access, request.getWhatsappMessageId()),
                        (access, connection, api, whatsappMessage) -> {
                            boolean hasMedia = whatsappMessage.isOutbound()
                                    ? whatsappMessage.getMessage().getType().isMediaFile()
                                    : whatsappMessage.getInMessage().getType().isMediaFile();

                            if (!hasMedia)
                                return super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.INVALID_MESSAGE_TYPE_MEDIA);

                            if (whatsappMessage.getMediaFileDetail() != null
                                    && !whatsappMessage.getMediaFileDetail().isEmpty())
                                return Mono.just(whatsappMessage);

                            String mediaId = whatsappMessage.isOutbound()
                                    ? whatsappMessage.getMessage().getMediaId()
                                    : whatsappMessage.getInMessage().getMediaId();

                            if (mediaId == null || mediaId.isBlank()) return Mono.just(whatsappMessage);

                            return FlatMapUtil.flatMapMono(
                                    () -> api.retrieveMediaUrl(mediaId),
                                    media -> api.downloadMediaFile(media.getUrl()),
                                    (media, mediaFile) -> this.makeFileInFiles(
                                            access.getClientCode(),
                                            mediaFile.getFileName(),
                                            this.createImagePath(whatsappMessage),
                                            mediaFile.getContent()),
                                    (media, mediaFile, fileDetails) ->
                                            this.updateInternal(whatsappMessage.setMediaFileDetail(fileDetails)));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.downloadMediaFile"));
    }

    private String createImagePath(WhatsappMessage whatsappMessage) {
        String direction = whatsappMessage.isOutbound() ? "outgoing" : "incoming";
        return Paths.get(
                        WHATSAPP_CLOUD_MESSAGE_LOCATION,
                        direction,
                        whatsappMessage.getBase64CustomerPhoneNumber(),
                        whatsappMessage.getCode())
                .toString();
    }

    private Mono<FileDetail> makeFileInFiles(
            String clientCode, String fileName, String fileLocation, byte[] fileBytes) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);

            String finalFileName = StringUtil.safeIsBlank(fileName) ? "file" : fileName;

            return this.fileService.create("static", clientCode, false, fileLocation, finalFileName, buffer);
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }
}
