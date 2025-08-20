package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.math.BigInteger;
import java.util.List;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public interface IProcessorAccessService {

    ProcessorMessageResourceService getMsgService();

    IFeignSecurityService getSecurityService();

    default Mono<ProcessorAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> this.getProcessorAccess(ca));
    }

    default Mono<ProcessorAccess> hasPublicAccess() {
        return FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, this::getProcessorAccess);
    }

    private Mono<ProcessorAccess> getProcessorAccess(ContextAuthentication ca) {

        if (ca.isAuthenticated())
            return this.getUserInheritanceInfo(ca)
                    .map(userInheritanceInfo ->
                            ProcessorAccess.of(ca, userInheritanceInfo.getT1(), userInheritanceInfo.getT2()));

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                acTup -> this.getHasAccessFlag(acTup, ca),
                (acTup, hasAppAccess) -> this.getUserInheritanceInfo(ca),
                (acTup, hasAppAccess, userInheritanceInfo) -> Mono.just(ProcessorAccess.of(
                        acTup.getT1(),
                        acTup.getT2(),
                        ca.getLoggedInFromClientCode(),
                        ca.getUser().getId(),
                        hasAppAccess,
                        userInheritanceInfo.getT1(),
                        userInheritanceInfo.getT2(),
                        ca.getUser())));
    }

    private Mono<Boolean> getHasAccessFlag(Tuple2<String, String> acTup, ContextAuthentication ca) {
        return Mono.zip(
                this.getSecurityService()
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> clientCodes.contains(acTup.getT2()))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                        acTup.getT2())),
                this.getSecurityService()
                        .isUserBeingManaged(ca.getUser().getId(), acTup.getT2())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT,
                                        ca.getUser().getId(),
                                        acTup.getT2())),
                (hasAppAccess, isUserManaged) -> BooleanUtil.safeValueOf(hasAppAccess && isUserManaged));
    }

    private Mono<Tuple2<List<BigInteger>, List<BigInteger>>> getUserInheritanceInfo(ContextAuthentication ca) {
        return Mono.zip(
                this.getSecurityService()
                        .getUserSubOrgInternal(
                                ca.getUser().getId(),
                                ca.getUrlAppCode(),
                                ca.getUser().getClientId()),
                BusinessPartnerConstant.isBpManager(ca.getUser().getAuthorities())
                        ? this.getSecurityService()
                                .getClientHierarchy(ca.getUser().getClientId())
                        : Mono.just(List.of()));
    }
}
