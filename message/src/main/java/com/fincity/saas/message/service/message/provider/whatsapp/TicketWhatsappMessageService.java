package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.message.provider.whatsapp.TicketWhatsappMessageRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.entity.processor.model.Ticket;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.IMessageAccessService;
import com.fincity.saas.message.service.message.MessageConnectionService;
import com.fincity.saas.message.util.PhoneUtil;
import java.math.BigInteger;
import lombok.Setter;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketWhatsappMessageService implements IMessageAccessService {

    private final WhatsappMessageService whatsappMessageService;
    private final WhatsappPhoneNumberService whatsappPhoneNumberService;
    private final WhatsappBusinessAccountService businessAccountService;
    private final IFeignEntityProcessorService entityProcessorService;
    private final MessageConnectionService messageConnectionService;
    private final MessageResourceService msgService;

    @Setter
    private IFeignSecurityService securityService;

    public TicketWhatsappMessageService(
            WhatsappMessageService whatsappMessageService,
            WhatsappPhoneNumberService whatsappPhoneNumberService,
            WhatsappBusinessAccountService businessAccountService,
            IFeignEntityProcessorService entityProcessorService,
            MessageConnectionService messageConnectionService,
            MessageResourceService msgService) {
        this.whatsappMessageService = whatsappMessageService;
        this.whatsappPhoneNumberService = whatsappPhoneNumberService;
        this.businessAccountService = businessAccountService;
        this.entityProcessorService = entityProcessorService;
        this.messageConnectionService = messageConnectionService;
        this.msgService = msgService;
    }

    @Override
    public MessageResourceService getMsgService() {
        return this.msgService;
    }

    @Override
    public IFeignSecurityService getSecurityService() {
        return this.securityService;
    }

    private <T> Mono<T> throwMissingParam(String field) {
        return msgService
                .getMessage(MessageResourceService.MISSING_MESSAGE_PARAMETERS, field)
                .flatMap(msg -> Mono.error(new GenericException(HttpStatus.BAD_REQUEST, msg)));
    }

    private Mono<Ticket> getAndValidateTicket(MessageAccess access, BigInteger ticketId) {
        return this.entityProcessorService
                .getTicketInternal(access.getAppCode(), access.getClientCode(), ticketId)
                .filter(ticket -> ticket != null && ticket.getProductId() != null)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        "Ticket",
                        ticketId.toString()))
                .filter(ticket -> ticket.getPhoneNumber() != null)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.MISSING_MESSAGE_PARAMETERS,
                        "Ticket phone number"));
    }

    private PhoneNumber createTicketPhoneNumber(Ticket ticket) {
        return PhoneNumber.of(
                ticket.getDialCode() != null ? ticket.getDialCode() : PhoneUtil.getDefaultCallingCode(),
                ticket.getPhoneNumber());
    }

    private Mono<WhatsappBusinessAccount> getWhatsappBusinessAccount(MessageAccess access, Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return this.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return this.businessAccountService.getBusinessAccount(access, businessAccountId);
    }

    private Mono<WhatsappPhoneNumber> getWhatsappPhoneNumberByProduct(
            MessageAccess access, ULong businessAccountId, ULong productId) {
        return this.whatsappPhoneNumberService
                .getByProductId(access, productId)
                .filter(phoneNumber ->
                        phoneNumber.getWhatsappBusinessAccountId().equals(businessAccountId))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        "WhatsApp phone number",
                        "product"));
    }

    public Mono<Message> sendMessageByTicketId(TicketWhatsappMessageRequest request) {

        if (request.isConnectionNull())
            return this.throwMissingParam(com.fincity.saas.message.model.base.BaseMessageRequest.Fields.connectionName);

        if (!request.isValid()) return this.throwMissingParam(TicketWhatsappMessageRequest.Fields.message);

        if (request.getMessage().getType().isMediaFile()
                && (request.getFileDetail() == null || request.getFileDetail().isEmpty()))
            return this.throwMissingParam(WhatsappMessage.Fields.mediaFileDetail);

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.getAndValidateTicket(
                                access, request.getTicketId().getId()),
                        (access, ticket) -> this.messageConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), request.getConnectionName()),
                        (access, ticket, connection) -> this.getWhatsappBusinessAccount(access, connection),
                        (access, ticket, connection, businessAccount) -> this.getWhatsappPhoneNumberByProduct(
                                access, businessAccount.getId(), ULongUtil.valueOf(ticket.getProductId())),
                        (access, ticket, connection, businessAccount, whatsappPhoneNumber) -> {
                            var ticketPhone = this.createTicketPhoneNumber(ticket);
                            var message = request.getMessage();
                            message.setTo(ticketPhone.getNumber());
                            return this.whatsappMessageService.sendMessageInternal(
                                    access,
                                    connection,
                                    whatsappPhoneNumber.getIdentity(),
                                    WhatsappMessage.ofOutbound(
                                            message,
                                            PhoneUtil.parse(access.getUser().getPhoneNumber()),
                                            request.getFileDetail()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketWhatsappMessageService.sendMessageByTicketId"));
    }
}
