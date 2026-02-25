package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.dto.Client;
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
                    .map(userInheritanceInfo -> ProcessorAccess.of(ca, userInheritanceInfo));

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                acTup -> this.getHasAccessFlag(acTup, ca),
                (acTup, hasAppAccess) -> this.getUserInheritanceInfo(ca),
                (acTup, hasAppAccess, userInheritanceInfo) -> Mono.just(ProcessorAccess.of(
                        acTup.getT1(), acTup.getT2(), hasAppAccess, ca.getUser(), userInheritanceInfo)));
    }

    private Mono<Boolean> getHasAccessFlag(Tuple2<String, String> acTup, ContextAuthentication ca) {

        return FlatMapUtil.flatMapMono(
                () -> this.getSecurityService()
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> clientCodes.contains(acTup.getT2()))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                        acTup.getT2())),
                hasAppAccess -> {
                    if (!hasAppAccess.booleanValue()) return Mono.just(false);

                    Mono<BigInteger> clientIdMono = this.getSecurityService()
                            .getClientByCode(acTup.getT2())
                            .map(Client::getId);

                    return clientIdMono.flatMap(clientId -> this.getSecurityService()
                            .isUserClientManageClient(
                                    ca.getUrlAppCode(),
                                    ca.getUser().getId(),
                                    ca.getUser().getClientId(),
                                    clientId)
                            .flatMap(BooleanUtil::safeValueOfWithEmpty)
                            .switchIfEmpty(this.getMsgService()
                                    .throwMessage(
                                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                            ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT,
                                            ca.getUser().getId(),
                                            acTup.getT2())));
                });
    }

    private Mono<ProcessorAccess.UserInheritanceInfo> getUserInheritanceInfo(ContextAuthentication ca) {

        Mono<List<BigInteger>> userSubOrgMono = this.getSecurityService()
                .getUserSubOrgInternal(
                        ca.getUser().getId(), ca.getUrlAppCode(), ca.getUser().getClientId());

        boolean hasOwnerRole = SecurityContextUtil.hasAuthority(
                BusinessPartnerConstant.OWNER_ROLE, ca.getUser().getAuthorities());
        boolean hasManagerRole = !hasOwnerRole
                && SecurityContextUtil.hasAuthority(
                        BusinessPartnerConstant.BP_MANAGER_ROLE, ca.getUser().getAuthorities());

        Mono<List<BigInteger>> managingClientMono;
        if (hasOwnerRole) {
            managingClientMono =
                    this.getSecurityService().getManagingClientIds(ca.getUser().getClientId());
        } else if (hasManagerRole) {
            managingClientMono = this.getSecurityService()
                    .getClientIdsOfManager(ca.getUser().getId())
                    .defaultIfEmpty(List.of());
        } else {
            managingClientMono = Mono.just(List.of());
        }

        Mono<Client> managedClientMono = BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(ca.getClientLevelType())
                ? this.getSecurityService()
                        .getManagedClientOfClientById(ca.getUser().getClientId())
                : Mono.empty();

        return Mono.zip(userSubOrgMono, managingClientMono, managedClientMono.defaultIfEmpty(new Client()))
                .map(userInheritTup -> ProcessorAccess.UserInheritanceInfo.of(ca, userInheritTup));
    }
}
