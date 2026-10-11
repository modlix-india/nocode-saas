package com.fincity.saas.commons.security.util;

import java.math.BigInteger;
import java.util.List;

import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;

/**
 * Whether a caller's client may act on, or read people data of, another client. Shared by the
 * KIRun security and message functions and by the message service's WhatsApp stream, so the rule
 * cannot drift between them.
 *
 * <p>Two directions, deliberately not symmetric:
 * <ul>
 * <li><b>Down</b>: the caller's client manages the target, at any depth. Unchanged from the
 * storage layer's rule.</li>
 * <li><b>Up</b>: the target manages the caller's client. This is the partner case: a CUSTOMER
 * client's users work the managing client's deals. A bare "manages, at any depth" would reach
 * SYSTEM and every other ancestor (every client is managed by SYSTEM), so the upward direction is
 * allowed only when all of these hold:
 * <ol>
 * <li>the target is the caller's client's DIRECT managing client (level 0 of its hierarchy), never a
 * grandparent or anything further up,</li>
 * <li>the target is not the system client,</li>
 * <li>the target has write access to {@code appCode} (it owns the app or has an EDIT_ACCESS row),
 * that is, it is the client whose data the partner works on for that app.</li>
 * </ol>
 * A blank {@code appCode} never allows the upward direction.</li>
 * </ul>
 */
public final class ClientRelationUtil {

    /** By id: the caller's client id and the target client id. */
    public static Mono<Boolean> isRelatedClient(IFeignSecurityService securityService, BigInteger callerClientId,
            BigInteger targetClientId, String appCode) {

        if (callerClientId == null || targetClientId == null)
            return Mono.just(Boolean.FALSE);

        if (callerClientId.equals(targetClientId))
            return Mono.just(Boolean.TRUE);

        return securityService.doesClientManageClient(callerClientId, targetClientId)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(down -> Boolean.TRUE.equals(down)
                        ? Mono.just(Boolean.TRUE)
                        : isAllowedAncestor(securityService, callerClientId, targetClientId, null, appCode));
    }

    /** By code: the caller's client code and the target client code. */
    public static Mono<Boolean> isRelatedClientCode(IFeignSecurityService securityService, String callerClientCode,
            String targetClientCode, String appCode) {

        if (StringUtil.safeIsBlank(callerClientCode) || StringUtil.safeIsBlank(targetClientCode))
            return Mono.just(Boolean.FALSE);

        if (callerClientCode.equals(targetClientCode))
            return Mono.just(Boolean.TRUE);

        return securityService.doesClientManageClientCode(callerClientCode, targetClientCode)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(down -> {
                    if (Boolean.TRUE.equals(down))
                        return Mono.just(Boolean.TRUE);

                    if (StringUtil.safeIsBlank(appCode))
                        return Mono.just(Boolean.FALSE);

                    return Mono.zip(securityService.getClientByCode(callerClientCode),
                            securityService.getClientByCode(targetClientCode))
                            .flatMap(t -> isAllowedAncestor(securityService, t.getT1().getId(), t.getT2().getId(),
                                    t.getT2(), appCode))
                            .defaultIfEmpty(Boolean.FALSE);
                });
    }

    /**
     * The narrow rule for acting AS a tenant (sending from its numbers, using its call account, hearing
     * its conversations): the target is the caller's own client, or the caller's client's direct
     * managing client when that is not SYSTEM and has write access to {@code appCode}. Clients the
     * caller manages are NOT included: managing a client does not mean speaking as it.
     */
    public static Mono<Boolean> isSelfOrDirectManagingClientCode(IFeignSecurityService securityService,
            String callerClientCode, String targetClientCode, String appCode) {

        if (StringUtil.safeIsBlank(callerClientCode) || StringUtil.safeIsBlank(targetClientCode))
            return Mono.just(Boolean.FALSE);

        if (callerClientCode.equals(targetClientCode))
            return Mono.just(Boolean.TRUE);

        if (StringUtil.safeIsBlank(appCode))
            return Mono.just(Boolean.FALSE);

        return Mono.zip(securityService.getClientByCode(callerClientCode),
                securityService.getClientByCode(targetClientCode))
                .flatMap(t -> isAllowedAncestor(securityService, t.getT1().getId(), t.getT2().getId(), t.getT2(),
                        appCode))
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * The upward direction only: the target is the caller's client's direct managing client, is not
     * SYSTEM, and has write access to the app. {@code target} may be passed when already read, to save a call.
     */
    static Mono<Boolean> isAllowedAncestor(IFeignSecurityService securityService, BigInteger callerClientId,
            BigInteger targetClientId, Client target, String appCode) {

        if (StringUtil.safeIsBlank(appCode) || callerClientId == null || targetClientId == null
                || callerClientId.equals(targetClientId))
            return Mono.just(Boolean.FALSE);

        // [caller, level 0, level 1, ...]: the target must be level 0, the direct managing client.
        return securityService.getClientHierarchy(callerClientId)
                .defaultIfEmpty(List.of())
                .flatMap(chain -> {
                    if (chain.size() < 2 || !targetClientId.equals(chain.get(1)))
                        return Mono.just(Boolean.FALSE);

                    Mono<Client> targetMono = target != null ? Mono.just(target)
                            : securityService.getClientById(targetClientId);

                    return targetMono
                            .flatMap(t -> {
                                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(t.getTypeCode())
                                        || StringUtil.safeIsBlank(t.getCode()))
                                    return Mono.just(Boolean.FALSE);

                                return securityService.hasWriteAccess(appCode, t.getCode())
                                        .defaultIfEmpty(Boolean.FALSE);
                            })
                            .defaultIfEmpty(Boolean.FALSE);
                });
    }

    private ClientRelationUtil() {
    }
}
