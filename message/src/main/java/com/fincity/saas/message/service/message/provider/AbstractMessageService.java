package com.fincity.saas.message.service.message.provider;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.feign.IFeignFileService;
import com.fincity.saas.message.model.common.IdAndValue;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.message.IMessageService;
import com.fincity.saas.message.service.message.MessageConnectionService;
import com.fincity.saas.message.service.message.MessageService;
import com.fincity.saas.message.service.message.MessageWebhookService;
import com.fincity.saas.message.service.message.event.MessageEventService;
import com.fincity.saas.message.util.PhoneUtil;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class AbstractMessageService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseProviderDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements IMessageService<D> {

    private static final String WEBHOOK_URI = "/api/message/webhook";

    private static final String SYSTEM = "SYSTEM";

    private static final String PAGE_URI = "/page";

    protected MessageConnectionService messageConnectionService;
    protected MessageEventService messageEventService;
    protected MessageService messageService;
    protected MessageWebhookService messageWebhookService;
    protected IFeignFileService fileService;

    @Value("${meta.webhook.verify-token:null}")
    protected String verifyToken;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

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

    @Lazy
    @Autowired
    private void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    @Lazy
    @Autowired
    private void setMessageWebhookService(MessageWebhookService messageWebhookService) {
        this.messageWebhookService = messageWebhookService;
    }

    @Lazy
    @Autowired
    private void setFileService(IFeignFileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.TEXT;
    }

    public Mono<D> updateInternalWithoutUser(MessageAccess publicAccess, D entity) {

        if (publicAccess.getUserId() != null) entity.setUpdatedBy(publicAccess.getUserId());

        return this.dao.update(entity).flatMap(updated -> this.evictCache(entity)
                .map(evicted -> updated));
    }

    public Mono<D> updateInternal(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(entity).map(evicted -> updated));
    }

    protected Mono<D> findByUniqueField(MessageAccess access, String id) {
        return this.dao.findByUniqueField(access, id);
    }

    protected Mono<Connection> isValidConnection(Connection connection) {
        if (connection.getConnectionType() != this.getConnectionType()
                || !connection.getConnectionSubType().equals(this.getConnectionSubType()))
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.INVALID_CONNECTION_TYPE,
                    connection.getConnectionType(),
                    connection.getConnectionSubType(),
                    this.getMessageSeries().getDisplayName());

        return Mono.just(connection);
    }

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_MESSAGE_PARAMETERS,
                this.getConnectionSubType().getProvider(),
                paramName);
    }

    protected Mono<String> getWebhookUrl(String appCode, String clientCode) {
        return buildWebhookUrl(appCode, clientCode != null ? clientCode : SYSTEM);
    }

    protected Mono<String> getWebhookAppUrl(String appCode) {
        return buildWebhookUrl(appCode, SYSTEM);
    }

    protected Mono<IdAndValue<ULong, PhoneNumber>> getUserIdAndPhone(ULong userId) {
        return this.securityService
                .getUserInternal(userId.toBigInteger(), null)
                .map(userResponse -> IdAndValue.of(
                        ULongUtil.valueOf(userResponse.getId()), PhoneUtil.parse(userResponse.getPhoneNumber())));
    }

    private Mono<String> buildWebhookUrl(String appCode, String clientCode) {
        return this.securityService
                .getAppUrl(appCode, clientCode.equals(SYSTEM) ? null : clientCode)
                .map(appUrl -> this.buildUrl(appUrl, appCode, clientCode, this.getProviderUri()))
                .switchIfEmpty(Mono.just(this.buildUrl(
                        this.appBaseUrl,
                        appCode,
                        clientCode,
                        this.getConnectionSubType().getProvider())));
    }

    private String buildUrl(String baseUrl, String appCode, String clientCode, String provider) {
        return baseUrl + "/" + appCode + "/" + clientCode + PAGE_URI + WEBHOOK_URI + "/" + provider;
    }
}
