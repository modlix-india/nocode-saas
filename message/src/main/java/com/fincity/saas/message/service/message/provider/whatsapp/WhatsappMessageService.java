package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
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
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappReadRequest;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import com.fincity.saas.message.model.response.MessageResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.WhatsappBodyText;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private IFeignEntityProcessorService entityProcessorService;
    private WhatsappWebhookSignatureService whatsappWebhookSignatureService;
    private EventDispatcher whatsappInboundDispatcher;

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
    public void setEntityProcessorService(IFeignEntityProcessorService entityProcessorService) {
        this.entityProcessorService = entityProcessorService;
    }

    @Autowired
    public void setBusinessAccountService(WhatsappBusinessAccountService businessAccountService) {
        this.businessAccountService = businessAccountService;
    }

    @Autowired
    public void setWhatsappWebhookSignatureService(WhatsappWebhookSignatureService whatsappWebhookSignatureService) {
        this.whatsappWebhookSignatureService = whatsappWebhookSignatureService;
    }

    @Autowired
    public void setEventDispatcher(EventDispatcher whatsappInboundDispatcher) {
        this.whatsappInboundDispatcher = whatsappInboundDispatcher;
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

    Mono<Message> sendMessageInternal(
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
                .getDefaultPhoneNumber(access, businessAccountId)
                .switchIfEmpty(super.throwMissingParam(WhatsappMessage.Fields.whatsappPhoneNumberId));
    }

    /**
     * Handles one webhook delivery, working out whose it is from the payload.
     *
     * <p>There is a single callback URL for the whole platform, configured once on the Meta app, so
     * nothing in the request identifies the tenant. It does not need to: {@code
     * metadata.phone_number_id} names a number, {@code MESSAGE_WHATSAPP_PHONE_NUMBERS} holds a
     * unique key on that column alone, and the row carries the app and client codes. The tenant is
     * therefore a property of the message rather than of the URL it arrived on, which is what makes
     * one URL workable and removes any per-tenant callback for two tenants on one Meta account to
     * fight over.
     *
     * <p>An unknown number is dropped, deliberately. It means a number that is live at Meta was
     * never synced here, and the only honest thing to do with a message for an account we do not
     * hold is nothing.
     *
     * @param signature and {@code rawPayload} establish that Meta actually sent this. Read the
     *     ordering carefully: the number lookup happens first, on an unverified body, purely to
     *     select which tenant's app secret to check the signature against. That is the same
     *     trade the business account id already made - it chooses a key, it grants nothing, and a
     *     forged body still fails the check unless the sender holds that tenant's secret.
     */
    public Mono<MessageResponse> processWebhookEvent(IWebHookEvent event, String signature, String rawPayload) {

        if (event == null || event.getEntry() == null) return Mono.empty();

        return this.whatsappPhoneNumberService
                .getByPhoneNumberIdInternal(phoneNumberIdOf(event))
                .flatMap(phoneNumber -> this.processWebhookEventFor(
                        phoneNumber.getAppCode(), phoneNumber.getClientCode(), event, signature, rawPayload));
    }

    private Mono<MessageResponse> processWebhookEventFor(
            String appCode, String clientCode, IWebHookEvent event, String signature, String rawPayload) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);

        return this.whatsappWebhookSignatureService
                .isTrusted(appCode, clientCode, wabaIdOf(event), signature, rawPayload)
                .flatMap(trusted -> Boolean.TRUE.equals(trusted)
                        ? this.processTrustedWebhookEvent(access, appCode, clientCode, event)
                        // Deliberately not 200. A forged payload is not an event we failed to
                        // process, and Meta never sees this response because Meta never sent it.
                        : this.msgService.<MessageResponse>throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                MessageResourceService.WEBHOOK_SIGNATURE_INVALID));
    }

    /**
     * Meta's business account id, used only to find which connection holds the app secret. Read
     * from an as-yet unverified body, which is safe: it selects a key, it does not grant anything.
     */
    private String wabaIdOf(IWebHookEvent event) {
        if (event.getEntry() == null || event.getEntry().isEmpty()) return null;
        return event.getEntry().getFirst().getId();
    }

    /**
     * The business number this delivery concerns, which is what the tenant is resolved from.
     *
     * <p>Scans rather than reading the first entry, because a delivery can batch several changes
     * and an empty one is possible. Every change we act on carries the same number: {@link
     * #processChange} handles messages and statuses only, and both sit in a {@code value} alongside
     * this metadata. Returns null when there is none, which drops the event.
     */
    private String phoneNumberIdOf(IWebHookEvent event) {

        for (IEntry entry : event.getEntry()) {
            if (entry.getChanges() == null) continue;

            for (IChange change : entry.getChanges()) {
                IValue value = change.getValue();
                if (value == null || value.getMetadata() == null) continue;

                String phoneNumberId = value.getMetadata().getPhoneNumberId();
                if (phoneNumberId != null && !phoneNumberId.isBlank()) return phoneNumberId;
            }
        }

        return null;
    }

    private Mono<MessageResponse> processTrustedWebhookEvent(
            MessageAccess access, String appCode, String clientCode, IWebHookEvent event) {

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

        String phoneNumberId = metadata != null ? metadata.getPhoneNumberId() : null;
        if (phoneNumberId == null) {
            logger.error("Phone number ID is null for incoming message: {}", iMessage.getId());
            return Mono.empty();
        }

        return FlatMapUtil.flatMapMono(
                        () -> this.whatsappPhoneNumberService.getByPhoneNumberId(access, phoneNumberId),
                        whatsappPhoneNumber -> this.resolveTicketId(access, whatsappPhoneNumber, iMessage),
                        (whatsappPhoneNumber, ticketId) -> this.dao
                                .findByUniqueField(iMessage.getId())
                                .flatMap(existing -> this.updateExistingMessage(
                                        access.setUserId(whatsappPhoneNumber.getUserId()),
                                        existing.setTicketId(ticketId.orElse(null)),
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
                                                        whatsappPhoneNumber.getId())
                                                .setTicketId(ticketId.orElse(null)))),
                        (whatsappPhoneNumber, ticketId, whatsappMessage) -> this.toMessage(whatsappMessage),
                        (whatsappPhoneNumber, ticketId, whatsappMessage, message) -> this.messageService
                                .createInternal(access, message)
                                .flatMap(msgCreated -> this.handOffToOwner(
                                                access, whatsappPhoneNumber, iMessage, metadata)
                                        .then(this.messageEventService.sendIncomingMessageEvent(
                                                access, whatsappMessage))
                                        .thenReturn(msgCreated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.processIncomingMessage"));
    }

    /**
     * Queues the message for the service that owns the number it arrived on.
     *
     * <p>Returns as soon as the outbox row is durable, so a consumer that is down delays delivery
     * rather than failing the webhook. That is the point of the outbox: Meta only needs to know we
     * have the message.
     *
     * <p>The local row written just above is transitional. entity-processor is becoming the system
     * of record for conversations, and this service keeps writing its own copy until that cutover
     * is verified, at which point both the write and the table go.
     */
    private Mono<Void> handOffToOwner(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, IMessage iMessage, IMetadata metadata) {

        PhoneNumber customerPhone =
                iMessage.getFrom() != null ? PhoneNumber.ofWhatsapp(iMessage.getFrom()) : null;

        WhatsappInboundDispatch dispatch = new WhatsappInboundDispatch()
                .setMetaMessageId(iMessage.getId())
                .setEventType(DispatchEventType.INBOUND_MESSAGE.name())
                .setProductId(
                        whatsappPhoneNumber.getProductId() != null
                                ? whatsappPhoneNumber.getProductId().toBigInteger()
                                : null)
                .setWhatsappPhoneNumberId(
                        whatsappPhoneNumber.getId() != null
                                ? whatsappPhoneNumber.getId().toBigInteger()
                                : null)
                .setWhatsappBusinessAccountId(
                        whatsappPhoneNumber.getWhatsappBusinessAccountId() != null
                                ? whatsappPhoneNumber
                                        .getWhatsappBusinessAccountId()
                                        .toBigInteger()
                                : null)
                .setWhatsappPhoneNumber(whatsappPhoneNumber.getDisplayPhoneNumber())
                .setCustomerWaId(iMessage.getFrom())
                .setCustomerDialCode(customerPhone != null ? customerPhone.getCountryCode() : null)
                .setCustomerPhoneNumber(customerPhone != null ? customerPhone.getNumber() : null)
                .setFrom(iMessage.getFrom())
                .setTo(metadata != null ? metadata.getDisplayPhoneNumber() : null)
                .setMessageType(iMessage.getType() != null ? iMessage.getType().name() : null)
                .setOccurredAt(inboundOccurredAt(iMessage))
                .setBodyText(WhatsappBodyText.of(iMessage))
                .setOutbound(Boolean.FALSE);

        return this.whatsappInboundDispatcher.enqueueAndDispatch(
                access,
                whatsappPhoneNumber.getOwnerService(),
                DispatchEventType.INBOUND_MESSAGE,
                dispatch.getMetaMessageId(),
                dispatch);
    }

    /**
     * A deal's WhatsApp thread, for internal callers only.
     *
     * <p>Deliberately takes a ticket id rather than a customer number: this service cannot tell
     * whether a caller may see a given deal, so it refuses to answer questions phrased in terms it
     * cannot authorize. entity-processor owns that check and is the only intended caller.
     */
    public Mono<Page<WhatsappMessage>> readByTicketInternal(
            String appCode, String clientCode, ULong ticketId, Pageable pageable) {

        if (ticketId == null) return Mono.just(Page.empty(pageable));

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return this.dao
                .messageAccessCondition(
                        FilterCondition.make(WhatsappMessage.Fields.ticketId, ticketId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        access)
                .flatMap(condition -> this.dao.readPageFilter(pageable, condition))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.readByTicketInternal"));
    }

    /**
     * Anchors an inbound message to a deal by handing it to entity-processor, which owns that
     * mapping and creates a deal when the customer has none.
     *
     * <p>Wrapped in {@link Optional} because {@code flatMapMono} drops the whole chain on an empty
     * signal, and "no matching ticket" must not stop the message being stored. A failed call is
     * logged and treated the same way: the message lands unassigned rather than being lost.
     */
    private Mono<Optional<ULong>> resolveTicketId(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, IMessage iMessage) {

        if (iMessage.getFrom() == null) return Mono.just(Optional.empty());

        PhoneNumber customerPhone = PhoneNumber.ofWhatsapp(iMessage.getFrom());

        return this.entityProcessorService
                .registerWhatsappMessage(
                        access.getAppCode(),
                        access.getClientCode(),
                        // Null means this number is the tenant default, so it serves every product
                        // and entity-processor matches on the customer's number alone.
                        whatsappPhoneNumber.getProductId() != null
                                ? whatsappPhoneNumber.getProductId().toBigInteger()
                                : null,
                        customerPhone.getNumber(),
                        inboundOccurredAt(iMessage).toString(),
                        // A stranger messaging the advertised number is a lead. The inbox is gated
                        // on deal access, so without a deal the message would be visible to nobody.
                        Boolean.TRUE)
                .map(ticket -> Optional.ofNullable(ULongUtil.valueOf(ticket.getId())))
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    logger.error("Could not register incoming message {} against a deal", iMessage.getId(), e);
                    return Mono.just(Optional.empty());
                });
    }

    /** Meta sends the send time as unix epoch seconds. Absent on some payloads, so fall back. */
    private LocalDateTime inboundOccurredAt(IMessage iMessage) {
        if (iMessage.getTimestamp() == null) return LocalDateTime.now(ZoneOffset.UTC);
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(iMessage.getTimestamp())), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
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
        return Flux.fromIterable(statuses)
                .flatMap(status -> this.processStatusUpdate(access, status))
                .then();
    }

    private Mono<Void> processStatusUpdate(MessageAccess access, IStatus status) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao
                                .findByUniqueField(status.getId())
                                .switchIfEmpty(super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.IDENTITY_WRONG,
                                        this.getMessageSeries().getDisplayName(),
                                        status.getId())),
                        whatsappMessage -> this.updateMessageStatus(access, whatsappMessage, status)
                                .onErrorResume(error -> super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.UNABLE_TO_UPDATE,
                                        this.getMessageSeries().getDisplayName(),
                                        status.getId())))
                .then();
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

        return super.updateInternalWithoutUser(access, whatsappMessage)
                .flatMap(updated -> this.handOffStatusToOwner(access, updated, status)
                        .thenReturn(updated));
    }

    /**
     * Queues a delivery receipt for the service that owns the number.
     *
     * <p>Rides the same outbox and keys on the same Meta message id as the message itself, which is
     * what lets a receipt that overtakes its own message still land: the owner upserts on that id
     * rather than assuming the message is already there.
     */
    private Mono<Void> handOffStatusToOwner(MessageAccess access, WhatsappMessage whatsappMessage, IStatus status) {

        if (whatsappMessage.getMessageId() == null) return Mono.empty();

        return this.whatsappPhoneNumberService
                .readById(access, whatsappMessage.getWhatsappPhoneNumberId())
                .flatMap(phoneNumber -> {
                    WhatsappInboundDispatch dispatch = new WhatsappInboundDispatch()
                            .setMetaMessageId(whatsappMessage.getMessageId())
                            .setEventType(DispatchEventType.MESSAGE_STATUS.name())
                            .setProductId(
                                    phoneNumber.getProductId() != null
                                            ? phoneNumber.getProductId().toBigInteger()
                                            : null)
                            .setWhatsappPhoneNumberId(
                                    phoneNumber.getId() != null
                                            ? phoneNumber.getId().toBigInteger()
                                            : null)
                            .setWhatsappPhoneNumber(phoneNumber.getDisplayPhoneNumber())
                            .setCustomerWaId(whatsappMessage.getCustomerWaId())
                            .setCustomerPhoneNumber(whatsappMessage.getCustomerPhoneNumber())
                            .setMessageStatus(status.getStatus() != null ? status.getStatus().name() : null)
                            .setOccurredAt(statusOccurredAt(status))
                            .setOutbound(whatsappMessage.isOutbound())
                            .setFailureReason(whatsappMessage.getFailureReason());

                    return this.whatsappInboundDispatcher.enqueueAndDispatch(
                            access,
                            phoneNumber.getOwnerService(),
                            DispatchEventType.MESSAGE_STATUS,
                            dispatch.getMetaMessageId(),
                            dispatch);
                })
                .onErrorResume(e -> {
                    // A lost receipt is a stale tick in the UI, not a lost message, so it must
                    // never fail the webhook that carried it.
                    logger.error(
                            "Could not hand the status of WhatsApp message {} to its owner.",
                            whatsappMessage.getMessageId(),
                            e);
                    return Mono.empty();
                });
    }

    private LocalDateTime statusOccurredAt(IStatus status) {
        if (status.getTimestamp() == null) return LocalDateTime.now(ZoneOffset.UTC);
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(status.getTimestamp())), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
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
                                .<Response>map(Tuple2::getT1))
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

    /**
     * Fetches a media file from Meta and stores it, returning where it landed.
     *
     * <p>Keyed on Meta's media id and nothing else, deliberately. The row-based {@link
     * #downloadMediaFile} cannot serve a caller whose messages live in another service: the ids
     * belong to different tables. Taking the media id instead keeps the split clean, this service
     * owns the Graph API and the file store, the owning service owns the row and decides who may
     * ask. It also survives this service's own message table going away.
     *
     * @param fileLocation caller-supplied so the owning service controls its own layout rather than
     *     inheriting one built from a row this service no longer has
     */
    public Mono<FileDetail> downloadMediaByMediaIdInternal(
            String appCode, String clientCode, String connectionName, String mediaId, String fileLocation) {

        if (mediaId == null || mediaId.isBlank())
            return super.throwMissingParam(WhatsappMediaRequest.Fields.whatsappMessageId);

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                        () -> this.messageConnectionService
                                .getCoreDocument(access.getAppCode(), access.getClientCode(), connectionName)
                                .flatMap(super::isValidConnection),
                        connection -> this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (connection, api) -> api.retrieveMediaUrl(mediaId),
                        (connection, api, media) -> api.downloadMediaFile(media.getUrl()),
                        (connection, api, media, mediaFile) -> this.makeFileInFiles(
                                access.getClientCode(),
                                mediaFile.getFileName(),
                                fileLocation == null || fileLocation.isBlank()
                                        ? WHATSAPP_CLOUD_MESSAGE_LOCATION
                                        : fileLocation,
                                mediaFile.getContent()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.downloadMediaByMediaIdInternal"));
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
