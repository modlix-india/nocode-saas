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

    protected MessageConnectionService messageConnectionService;
    protected MessageEventService messageEventService;
    protected MessageService messageService;
    protected MessageWebhookService messageWebhookService;
    protected IFeignFileService fileService;

    private static final String WEBHOOK_URI = "/api/message/webhooks";

    private static final String WEBHOOK_HOST = "modlix.com";

    @Value("${meta.webhook.verify-token:null}")
    protected String verifyToken;

    /**
     * Which environment this is, as {@code ""}, {@code ".dev"}, {@code ".stage"} or {@code
     * ".local"}, and the only thing that varies in a callback URL.
     *
     * <p>Reused rather than introducing a webhook-specific base URL property, because a second
     * property would be a second thing to get wrong per environment and would silently disagree
     * with this one. {@code IndexHTMLService.deriveBeaconHost} derives the authzump host from the
     * same value in the same way.
     */
    @Value("${security.appCodeSuffix:}")
    private String appCodeSuffix;

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

    protected Mono<IdAndValue<ULong, PhoneNumber>> getUserIdAndPhone(ULong userId) {
        return this.securityService
                .getUserInternal(userId.toBigInteger(), null)
                .map(userResponse -> IdAndValue.of(
                        ULongUtil.valueOf(userResponse.getId()), PhoneUtil.parse(userResponse.getPhoneNumber())));
    }

    /**
     * This environment's callback URL for this provider. One value, whoever is asking.
     *
     * <pre>
     *   ""       -> https://modlix.com/api/message/webhooks/whatsapp
     *   ".dev"   -> https://dev.modlix.com/api/message/webhooks/whatsapp
     *   ".stage" -> https://stage.modlix.com/api/message/webhooks/whatsapp
     * </pre>
     *
     * <p>Takes no {@code appCode} and no {@code clientCode}, and that is the point rather than an
     * omission. It used to compose
     * {@code <appUrl>/<appCode>/<clientCode>/page/api/message/webhooks/<provider>} off a per-tenant
     * app URL lookup, which was wrong twice: the client code in a Modlix URL names the client
     * <b>hosting</b> the application rather than the one consuming it, and a provider stores one
     * callback per business account, so giving each tenant a different URL meant two tenants
     * sharing an account silently overwrote each other's, last write winning.
     *
     * <p>Registering it per account is still necessary, just not per tenant. A provider account is
     * reachable by exactly one environment at a time, and this is what decides which. The tenant is
     * a property of the message, resolved on arrival from the number it came in on.
     *
     * <p>Synchronous because it is configuration, not a lookup. The {@code Mono} the previous
     * version returned only existed to wrap the app URL call that is now gone.
     */
    protected String getWebhookUrl() {
        return "https://" + environmentHost() + WEBHOOK_URI + "/"
                + this.getConnectionSubType().getProvider();
    }

    /**
     * Whether this environment is a developer machine, and so unreachable by any provider.
     *
     * <p>Matters because registering a callback is not a local action: it is written to the
     * provider's copy of a shared business account. A local machine claiming one points it at a
     * host the provider cannot resolve and takes it away from the environment that was serving it,
     * with no error anywhere. Anything that registers a callback without a human deciding to must
     * check this first.
     */
    protected boolean isLocalEnvironment() {
        return environmentHost().startsWith("local.");
    }

    /**
     * {@code ".dev"} to {@code dev.modlix.com}, blank to {@code modlix.com}.
     *
     * <p>Tolerates a suffix with or without its leading dot, and takes only the first segment, so a
     * compound value cannot leak into the host. Same handling as
     * {@code IndexHTMLService.deriveBeaconHost}.
     */
    private String environmentHost() {

        if (this.appCodeSuffix == null || this.appCodeSuffix.isBlank()) return WEBHOOK_HOST;

        String trimmed = this.appCodeSuffix.startsWith(".") ? this.appCodeSuffix.substring(1) : this.appCodeSuffix;
        int dotIdx = trimmed.indexOf('.');
        String env = dotIdx >= 0 ? trimmed.substring(0, dotIdx) : trimmed;

        return env.isBlank() ? WEBHOOK_HOST : env + "." + WEBHOOK_HOST;
    }
}
