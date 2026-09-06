package com.fincity.saas.ui.service;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.repository.PageRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PageService extends AbstractUIOverridableDataService<Page, PageRepository> {

    private ApplicationService appServiceForProps;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PageService.class);

    public PageService() {
        super(Page.class);
    }

    public void setApplicationService(ApplicationService appService) {
        this.appServiceForProps = appService;
    }

    @Override
    protected Mono<Page> updatableEntity(Page entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setDevice(entity.getDevice())
                            .setTranslations(entity.getTranslations())
                            .setProperties(entity.getProperties())
                            .setEventFunctions(entity.getEventFunctions())
                            .setRootComponent(entity.getRootComponent())
                            .setComponentDefinition(entity.getComponentDefinition());

                    existing.setVersion(existing.getVersion() + 1);

                    // Full page PUT: increment all per-component and per-event versions
                    // so any concurrent component-level PATCHes get a 412 on retry.
                    incrementAllComponentVersions(existing);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.updatableEntity"));
    }

    @Override
    public Mono<ObjectWithUniqueID<Page>> read(String name, String appCode, String clientCode) {

        return super.read(name, appCode, clientCode).flatMap(pg -> {

            if (StringUtil.safeIsBlank(pg.getObject().getPermission()))
                return Mono.just(pg);

            return flatMapMono(

                    SecurityContextUtil::getUsersContextAuthentication,

                    ca -> Mono.just(ca.isAuthenticated()),

                    (ContextAuthentication ca, @Nonnull Boolean isAuthenticated) -> {

                        if (isAuthenticated)
                            return Mono.just(pg);

                        return flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

                                props -> {

                                    if (StringUtil.safeIsBlank(props.get("loginPage")))
                                        return Mono.just(pg);

                                    return super.read(props.get("loginPage")
                                            .toString(), appCode, clientCode);
                                }).contextWrite(
                                        Context.of(LogUtil.METHOD_NAME, "PageService.read [Looking for Login page]"));
                    }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.read"));
        })
                .switchIfEmpty(Mono
                        .defer(() -> flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

                                props -> {

                                    if (StringUtil.safeIsBlank(props.get("notFoundPage")))
                                        return Mono.empty();

                                    return super.read(props.get("notFoundPage")
                                            .toString(), appCode, clientCode);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME,
                                        "PageService.read [Looking for Not found page]"))));
    }

    @Override
    protected Mono<ObjectWithUniqueID<Page>> applyChange(String name, String appCode, String clientCode, Page page,
            String checksumString) {

        return flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appServiceForProps.readProperties(appCode, appCode, clientCode),

                (ca, props) -> {
                    if (!StringUtil.safeIsBlank(page.getPermission())) {
                        logger.info("Permission for page {} are {} and with auth : {}", name, page.getPermission(), ca);
                    }
                    if (ca.isAuthenticated()
                            && !SecurityContextUtil.hasAuthority(page.getPermission(), ca.getAuthorities())) {

                        if (StringUtil.safeIsBlank(props.get("forbiddenPage")))
                            return this.messageResourceService.throwMessage(
                                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                    AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, page.getPermission());

                        String fbName = props.get("forbiddenPage").toString();

                        if (!fbName.equals(name))
                            return super.read(fbName, appCode, clientCode);
                    }

                    return Mono.just(new ObjectWithUniqueID<>(page, checksumString));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.applyChange"))
                .defaultIfEmpty(new ObjectWithUniqueID<>(page, checksumString));

    }

    /**
     * Increment all per-component and per-event-function versions.
     * Called during full page PUT to invalidate any concurrent component-level PATCHes.
     */
    private void incrementAllComponentVersions(Page page) {
        // Component versions
        Map<String, Integer> cv = page.getComponentVersions();
        if (cv == null) cv = new HashMap<>();
        Map<String, com.fincity.saas.ui.model.ComponentDefinition> compDef = page.getComponentDefinition();
        if (compDef != null) {
            for (String key : compDef.keySet())
                cv.put(key, cv.getOrDefault(key, 0) + 1);
        }
        page.setComponentVersions(cv);

        // Event function versions
        Map<String, Integer> ev = page.getEventFunctionVersions();
        if (ev == null) ev = new HashMap<>();
        Map<String, Object> eventFns = page.getEventFunctions();
        if (eventFns != null) {
            for (String key : eventFns.keySet())
                ev.put(key, ev.getOrDefault(key, 0) + 1);
        }
        page.setEventFunctionVersions(ev);
    }

    // ── Component-level PATCH ─────────────────────────────────────
    //
    // Enables concurrent edits to different components on the same page.
    // PATCH on 'btnSubmit' does NOT conflict with PATCH on 'headerNav'.
    // Version check is per-component-key, not whole-document.

    /**
     * The shape both component-level patches must use: authorize the STORED
     * document, patch the MERGED one, write back only the delta.
     *
     * Both halves of this were wrong before, and both only bite on derived
     * documents, which is why it survived: nearly all PATCH traffic runs on root
     * documents where a delta and a merge are the same thing. Cross-client editing
     * makes every edited document derived, so both become routine.
     *
     * 1. Authorization ran `read(pageId)` and nothing else. That is the READ check
     * (checkAppWriteAccess = false), and inheritance grants a derived client read
     * on every ancestor's document, so any client in the chain could PATCH a
     * component straight into the base client's live page with no write access
     * anywhere in the picture.
     *
     * 2. `read()` returns the MERGED page and `repo.save(existing)` wrote that back
     * over the stored row. On a derived document that flattens the delta: the row
     * stops being an override, freezes a copy of whatever the base held at that
     * moment, and silently stops inheriting.
     *
     * The merged page is still what gets patched, and must be -- a delta legitimately
     * does not contain the component being edited, so patching the stored row
     * directly would 404 on every inherited component. Extracting the override
     * afterwards is what keeps the row a delta, and is the same
     * getMergedSources/extractOverride pair that update() runs.
     */
    private Mono<Page> patchOverridable(String pageId, java.util.function.Function<Page, Mono<Page>> patch) {

        return FlatMapUtil.<ContextAuthentication, Page, Boolean, Page, Page, Page>flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> StringUtil.safeIsBlank(pageId) ? Mono.empty() : this.repo.findById(pageId),

                (ca, stored) -> this.accessCheck(ca, UPDATE, stored.getAppCode(), stored.getClientCode(), true),

                (ca, stored, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? this.read(pageId) : Mono.empty(),

                (ca, stored, hasAccess, merged) -> patch.apply(merged),

                (ca, stored, hasAccess, merged, patched) -> this.getMergedSources(patched)
                        .flatMap(sources -> this.extractOverride(patched, sources))
                        .defaultIfEmpty(patched)
                        .flatMap(this.repo::save))

                .flatMap(this::evictRecursively)
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, "Page"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchOverridable"));
    }

    /**
     * Patch a single component within a page with per-component version checking.
     *
     * @param pageId                  Page MongoDB ID
     * @param componentKey            Component key to update
     * @param componentData           The updated ComponentDefinition fields (merged onto existing)
     * @param expectedComponentVersion Expected version of this specific component (0 = skip check)
     * @param message                 Change description for version history
     */
    public Mono<Page> patchComponent(String pageId, String componentKey,
            Map<String, Object> componentData, int expectedComponentVersion, String message) {

        return this.patchOverridable(pageId,

                existing -> {
                    Map<String, ComponentDefinition> compDef = existing.getComponentDefinition();
                    if (compDef == null || !compDef.containsKey(componentKey))
                        return this.messageResourceService.<Page>throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                                "Component", componentKey);

                    // Per-component version check
                    Map<String, Integer> cv = existing.getComponentVersions();
                    if (cv != null && expectedComponentVersion > 0) {
                        int currentCv = cv.getOrDefault(componentKey, 1);
                        if (currentCv != expectedComponentVersion)
                            return this.messageResourceService.<Page>throwMessage(
                                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                    AbstractMongoMessageResourceService.VERSION_MISMATCH);
                    }

                    // Merge component data onto existing component
                    ComponentDefinition comp = compDef.get(componentKey);
                    ComponentDefinition updated = this.objectMapper.convertValue(
                            componentData, ComponentDefinition.class);
                    if (updated.getProperties() != null)
                        comp.setProperties(updated.getProperties());
                    if (updated.getStyleProperties() != null)
                        comp.setStyleProperties(updated.getStyleProperties());
                    if (updated.getChildren() != null)
                        comp.setChildren(updated.getChildren());
                    if (updated.getDisplayOrder() != null)
                        comp.setDisplayOrder(updated.getDisplayOrder());
                    if (updated.getBindingPath() != null)
                        comp.setBindingPath(updated.getBindingPath());
                    if (updated.getBindingPath2() != null)
                        comp.setBindingPath2(updated.getBindingPath2());
                    if (updated.getBindingPath3() != null)
                        comp.setBindingPath3(updated.getBindingPath3());
                    if (updated.getBindingPath4() != null)
                        comp.setBindingPath4(updated.getBindingPath4());
                    if (updated.getBindingPath5() != null)
                        comp.setBindingPath5(updated.getBindingPath5());
                    if (updated.getBindingPath6() != null)
                        comp.setBindingPath6(updated.getBindingPath6());

                    // Increment per-component version
                    if (cv == null) cv = new HashMap<>();
                    cv.put(componentKey, cv.getOrDefault(componentKey, 1) + 1);
                    existing.setComponentVersions(cv);

                    // Increment whole-doc version
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setMessage(message);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchComponent"));
    }

    /**
     * Patch a single event function within a page with per-event version checking.
     *
     * @param pageId                  Page MongoDB ID
     * @param eventName               Event function name
     * @param eventDefinition         The full event function definition (replaces existing)
     * @param expectedEventVersion    Expected version of this event function (0 = skip check)
     * @param message                 Change description
     */
    public Mono<Page> patchEventFunction(String pageId, String eventName,
            Map<String, Object> eventDefinition, int expectedEventVersion, String message) {

        return this.patchOverridable(pageId,

                existing -> {
                    // Per-event version check
                    Map<String, Integer> ev = existing.getEventFunctionVersions();
                    if (ev != null && expectedEventVersion > 0) {
                        int currentEv = ev.getOrDefault(eventName, 1);
                        if (currentEv != expectedEventVersion)
                            return this.messageResourceService.<Page>throwMessage(
                                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                    AbstractMongoMessageResourceService.VERSION_MISMATCH);
                    }

                    // Set event function
                    Map<String, Object> eventFunctions = existing.getEventFunctions();
                    if (eventFunctions == null) eventFunctions = new HashMap<>();
                    eventFunctions.put(eventName, eventDefinition);
                    existing.setEventFunctions(eventFunctions);

                    // Increment per-event version
                    if (ev == null) ev = new HashMap<>();
                    ev.put(eventName, ev.getOrDefault(eventName, 1) + 1);
                    existing.setEventFunctionVersions(ev);

                    // Increment whole-doc version
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setMessage(message);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchEventFunction"));
    }

    @Override
    public Mono<Page> update(Page page) {
        return super.update(page)
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_PAGE + "-" + page.getAppCode()));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id),

                page -> super.delete(id),

                (page, deleted) -> this.cacheService.evictAll(EngineService.CACHE_NAME_PAGE + "-" + page.getAppCode()),

                (page, deleted, cacheEvicted) -> this.ssrCacheEvictionService.evictByAppCode(page.getAppCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.delete"));
    }
}
