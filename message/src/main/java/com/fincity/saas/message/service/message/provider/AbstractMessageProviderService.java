package com.fincity.saas.message.service.message.provider;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.ProviderIdentifier;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.ProviderIdentifierService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.message.IMessageService;
import com.fincity.saas.message.service.message.MessageConnectionService;
import com.fincity.saas.message.service.message.MessageService;
import com.fincity.saas.message.service.message.event.MessageEventService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class AbstractMessageProviderService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseProviderDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements IMessageService<D> {

    protected MessageService messageService;
    protected MessageConnectionService messageConnectionService;
    protected MessageEventService messageEventService;
    protected ProviderIdentifierService providerIdentifierService;

    @Lazy
    @Autowired
    private void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    @Lazy
    @Autowired
    private void setMessageConnectionService(MessageConnectionService messageConnectionService) {
        this.messageConnectionService = messageConnectionService;
    }

    @Lazy
    @Autowired
    private void setMessageEventService(MessageEventService messageEventService) {
        this.messageEventService = messageEventService;
    }

    @Autowired
    private void setProviderIdentifierService(ProviderIdentifierService providerIdentifierService) {
        this.providerIdentifierService = providerIdentifierService;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.TEXT_MESSAGE;
    }

    protected Mono<Boolean> isValidConnection(Connection connection) {
        if (connection.getConnectionType() != this.getConnectionType()
                || connection.getConnectionSubType().equals(this.getConnectionSubType()))
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.INVALID_CONNECTION_TYPE);

        return Mono.just(Boolean.TRUE);
    }

    protected Mono<D> findByUniqueField(String messageId) {
        return this.dao.findByUniqueField(messageId);
    }

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CALL_PARAMETERS,
                this.getConnectionSubType().getProvider(),
                paramName);
    }

    protected Mono<ProviderIdentifier> validateProviderIdentifier(
            String appCode, String clientCode, String identifier) {
        return this.providerIdentifierService
                .getProviderIdentifier(
                        appCode, clientCode, this.getConnectionType(), this.getConnectionSubType(), identifier)
                .switchIfEmpty(Mono.defer(() -> {
                    logger.error(
                            "No provider identifier found for identifier: {}, appCode: {}, clientCode: {}, connectionType: {}, connectionSubType: {}",
                            identifier,
                            appCode,
                            clientCode,
                            this.getConnectionType(),
                            this.getConnectionSubType());
                    return Mono.empty();
                }));
    }
}
