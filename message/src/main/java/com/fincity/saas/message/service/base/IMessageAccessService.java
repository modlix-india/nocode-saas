package com.fincity.saas.message.service.base;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;

import reactor.core.publisher.Mono;

public interface IMessageAccessService {

    MessageResourceService getMsgService();

    IFeignSecurityService getSecurityService();

    default Mono<MessageAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> this.getMessageAccess(ca));
    }

    default Mono<MessageAccess> hasPublicAccess() {
        return FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, this::getMessageAccess);
    }

    private Mono<MessageAccess> getMessageAccess(ContextAuthentication ca) {

        if (ca.getUser().getPhoneNumber() == null)
            return this.getMsgService()
                    .throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.PHONE_NUMBER_REQUIRED);

        if (ca.isAuthenticated())
            return Mono.just(MessageAccess.of(ca));

        return SecurityContextUtil.resolveAppAndClientCode(null, null)
                .map(acTup -> MessageAccess.of(
                        acTup.getT1(),
                        acTup.getT2(),
                        ULongUtil.valueOf(ca.getUser().getId()),
                        Boolean.TRUE,
                        ca.getUser()));
    }

    /**
     * Gate on the client that owns the row being touched, letting it through or failing with 403.
     *
     * <p>The question is pure hierarchy: is this the caller's own client, or one beneath it.
     * Deliberately <b>not</b> {@code isUserClientManageClient}, which additionally demands the caller
     * be a registered client manager. That role exists for administering the channel partners
     * sitting under a tenant, so requiring it here would answer a question nobody asked and lock an
     * ordinary owner out of their own settings.
     *
     * <p>Only user traffic is checked. Webhooks and provider callbacks run with no authenticated
     * user and legitimately write rows belonging to whichever tenant the event is for, so gating
     * them on a caller's client would stop message delivery rather than protect anything. Their
     * protection is upstream: signature verification on the way in and nginx on {@code /internal}.
     *
     * <p>Same-client is the overwhelming majority of calls and is answered without leaving the
     * process, so this costs a round trip only when a row genuinely belongs to someone else.
     */
    default <T> Mono<T> withManagingClient(String targetClientCode, T entity) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    if (!ca.isAuthenticated()
                            || targetClientCode == null
                            || targetClientCode.equals(ca.getClientCode())) return Mono.just(entity);

                    return this.getSecurityService()
                            .doesClientManageClientCode(ca.getClientCode(), targetClientCode)
                            .flatMap(BooleanUtil::safeValueOfWithEmpty)
                            .map(canManage -> entity)
                            .switchIfEmpty(this.getMsgService()
                                    .throwMessage(
                                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                            MessageResourceService.INVALID_USER_FOR_CLIENT,
                                            ca.getUser().getId(),
                                            targetClientCode));
                })
                .defaultIfEmpty(entity);
    }
}
