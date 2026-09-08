package com.fincity.saas.commons.mongo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * App-level view over unpublished work, shared by every module that drafts.
 *
 * Per-object publish lives on each object's own controller, inherited from
 * AbstractOverridableDataController. This adds the two things that only make
 * sense across an app: what is pending, and ship all of it.
 *
 * It lives here rather than in `ui` because `core` needs exactly the same thing
 * and for the same reason: a change that spans a page and the storage it reads is
 * one unit of work to whoever made it, and publishing it a service at a time is
 * not a thing anyone wants to do by hand. Subclasses supply their own service
 * list; nothing else differs.
 */
public abstract class AbstractPublishService {

    private final Map<String, AbstractOverridableDataService<?, ?>> servicesByObjectType = new HashMap<>();

    private final AbstractDraftService draftService;

    private final FeignAuthenticationService securityService;

    private final AbstractMongoMessageResourceService messageResourceService;

    protected AbstractPublishService(AbstractDraftService draftService, FeignAuthenticationService securityService,
            AbstractMongoMessageResourceService messageResourceService,
            List<AbstractOverridableDataService<?, ?>> services) {

        this.draftService = draftService;
        this.securityService = securityService;
        this.messageResourceService = messageResourceService;

        for (AbstractOverridableDataService<?, ?> service : services)
            this.servicesByObjectType.put(service.getObjectName().toUpperCase(), service);
    }

    /**
     * Everything in this app with unpublished work, grouped by object type.
     * One indexed query, which is why no hasDraft field is needed on the list
     * endpoints.
     */
    public Mono<Map<String, List<Map<String, Object>>>> pending(String appCode, String clientCode) {

        return this.authorizedClientCode(appCode, clientCode, false)
                .flatMap(cc -> this.draftService.pending(appCode, cc)
                        .collectList()
                        .map(drafts -> {
                            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
                            for (Draft draft : drafts)
                                byType.computeIfAbsent(draft.getObjectType(), k -> new ArrayList<>())
                                        .add(this.summarize(draft));
                            return byType;
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PublishService.pending"));
    }

    private Map<String, Object> summarize(Draft draft) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("objectType", draft.getObjectType());
        summary.put("name", draft.getObjectName());
        summary.put("objectId", draft.getObjectId());
        summary.put("clientCode", draft.getClientCode());
        summary.put("baseVersion", draft.getBaseVersion());
        summary.put("message", draft.getMessage());
        summary.put("updatedAt", draft.getUpdatedAt());
        summary.put("updatedBy", draft.getUpdatedBy());
        return summary;
    }

    /**
     * Publish every pending draft for this app and client.
     *
     * Each object goes through its own service's publish(), so per-service cache
     * eviction stays correct. Sequential rather than parallel: publishing evicts
     * shared caches and a page can depend on a style or a shell page, so the
     * ordering being deterministic is worth more here than the wall-clock saving.
     *
     * A failure on one object does not abort the rest. The result reports each
     * outcome, because a partial publish that silently stopped halfway would be
     * worse than one that says which objects did not make it.
     */
    public Mono<Map<String, Object>> publishAll(String appCode, String clientCode) {

        return this.authorizedClientCode(appCode, clientCode, true)
                .flatMap(cc -> this.draftService.pending(appCode, cc)
                        .collectList()
                        .flatMap(drafts -> Flux.fromIterable(drafts)
                                .concatMap(this::publishOne)
                                .collectList()
                                .map(results -> {
                                    Map<String, Object> out = new LinkedHashMap<>();
                                    out.put("appCode", appCode);
                                    out.put("clientCode", cc);
                                    out.put("attempted", results.size());
                                    out.put("published",
                                            results.stream().filter(r -> Boolean.TRUE.equals(r.get("published")))
                                                    .count());
                                    out.put("results", results);
                                    return out;
                                })))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PublishService.publishAll"));
    }

    private Mono<Map<String, Object>> publishOne(Draft draft) {

        AbstractOverridableDataService<?, ?> service = this.servicesByObjectType.get(draft.getObjectType());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectType", draft.getObjectType());
        result.put("name", draft.getObjectName());
        result.put("objectId", draft.getObjectId());

        if (service == null) {
            result.put("published", Boolean.FALSE);
            result.put("error", "No publishable service for object type " + draft.getObjectType());
            return Mono.just(result);
        }

        return service.publish(draft.getObjectId(), draft.getMessage())
                .map(published -> {
                    result.put("published", Boolean.TRUE);
                    return result;
                })
                .onErrorResume(e -> {
                    result.put("published", Boolean.FALSE);
                    result.put("error", e.getMessage());
                    return Mono.just(result);
                });
    }

    /**
     * Resolve the client to operate on, and prove the caller is allowed to.
     *
     * This used to return the caller's `clientCode` query parameter verbatim with
     * no check of any kind. Every `ui` route is permitAll at the HTTP layer, since
     * UIConfiguration passes "/**" as its exclusion list and all authorization is
     * meant to come from the service, so these two methods were reachable
     * unauthenticated for any app and any client. `pending` returned the objectId
     * of every unpublished object, which is exactly what makes writing into another
     * tenant's draft possible.
     *
     * @param writeAccess publishing needs write on the app; listing needs only read.
     */
    private Mono<String> authorizedClientCode(String appCode, String clientCode, boolean writeAccess) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (!ca.isAuthenticated())
                        return Mono.empty();

                    // A supplied clientCode is a claim, not a fact: it is only
                    // honoured for a client the caller actually manages.
                    if (StringUtil.safeIsBlank(clientCode) || clientCode.equals(ca.getClientCode()))
                        return Mono.just(StringUtil.safeIsBlank(clientCode) ? ca.getClientCode() : clientCode);

                    if (ca.isSystemClient())
                        return Mono.just(clientCode);

                    return this.securityService.doesClientManageClientCode(ca.getClientCode(), clientCode)
                            .filter(BooleanUtil::safeValueOf)
                            .map(managed -> clientCode);
                },

                (ca, cc) -> writeAccess ? this.securityService.hasWriteAccess(appCode, ca.getClientCode())
                        : this.securityService.hasReadAccess(appCode, ca.getClientCode()),

                (ca, cc, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? Mono.just(cc) : Mono.empty())

                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS, appCode));
    }
}
