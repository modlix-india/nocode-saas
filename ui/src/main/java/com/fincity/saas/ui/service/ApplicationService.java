package com.fincity.saas.ui.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.MobileApp;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import com.fincity.saas.ui.repository.ApplicationRepository;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ApplicationService extends AbstractUIOverridableDataService<Application, ApplicationRepository> {

    private static final String CACHE_NAME_PROPERTIES = "cacheProperties";

    private PageService pageService;

    private UIFillerService fillerService;

    private MobileAppService mobileAppService;

    @Value("${security.appCodeSuffix:}")
    private String appCodeSuffix;

    @Autowired
    public ApplicationService(PageService pageService, UIFillerService fillerService,
            MobileAppService mobileAppService) {
        super(Application.class);
        this.pageService = pageService;
        this.fillerService = fillerService;
        this.mobileAppService = mobileAppService;
    }

    protected ApplicationService() {
        super(Application.class);
    }

    @PostConstruct
    public void init() {
        // this cyclic reference is need for picking shell page definition and the other
        // page definitions in the page service from application properties.
        this.pageService.setApplicationService(this);
    }

    @Override
    public Mono<Application> create(Application entity) {

        if (StringUtil.safeIsBlank(entity.getName()) || StringUtil.safeIsBlank(entity.getAppCode())
                || !StringUtil.safeEquals(entity.getName(), entity.getAppCode())
                || !StringUtil.onlyAlphabetAllowed(entity.getAppCode()))

            return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    UIMessageResourceService.APP_NAME_MISMATCH);

        return super.create(entity);
    }

    @Override
    public Mono<Application> update(Application entity) {

        return FlatMapUtil.flatMapMono(
                () -> super.update(entity),
                this::evictAll);
    }

    private Mono<Application> evictAll(Application e) {

        return FlatMapUtil.flatMapMono(
                () -> cacheService.evictAll(
                        this.getCacheName(e.getAppCode() + "_" + IndexHTMLService.CACHE_NAME_INDEX, e.getAppCode())),
                x -> cacheService.evictAll(
                        this.getCacheName(e.getAppCode() + "_" + ManifestService.CACHE_NAME_MANIFEST, e.getAppCode())),
                (x, y) -> cacheService
                        .evictAll(this.getCacheName(e.getAppCode() + "_" + CACHE_NAME_PROPERTIES, e.getAppCode())),
                (x, y, z) -> cacheService
                        .evictAll(EngineService.CACHE_NAME_APPLICATION + "-" + e.getAppCode()),
                (x, y, z, a) -> Mono.just(e));
    }

    private Mono<Boolean> evictAll(String appCode) {

        return FlatMapUtil.flatMapMono(
                () -> cacheService.evictAll(
                        this.getCacheName(appCode + "_" + IndexHTMLService.CACHE_NAME_INDEX, appCode)),
                x -> cacheService.evictAll(
                        this.getCacheName(appCode + "_" + ManifestService.CACHE_NAME_MANIFEST, appCode)),
                (x, y) -> cacheService
                        .evictAll(this.getCacheName(appCode + "_" + CACHE_NAME_PROPERTIES, appCode)),
                (x, y, z) -> cacheService
                        .evictAll(EngineService.CACHE_NAME_APPLICATION + "-" + appCode));
    }

    @Override
    public Mono<Boolean> delete(String id) {

        return this.read(id)
                .flatMap(e -> super.delete(id).flatMap(x -> this.evictAll(e).thenReturn(x)));
    }

    @Override
    protected Mono<Application> updatableEntity(Application entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setProperties(entity.getProperties())
                            .setTranslations(entity.getTranslations())
                            .setLanguages(entity.getLanguages());

                    existing.setMessage(entity.getMessage());
                    existing.setDefaultLanguage(entity.getDefaultLanguage());
                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.updatableEntity"));
    }

    public Mono<Map<String, Object>> readProperties(String name, String appCode, String clientCode) { // NOSONAR
        // This method is not complex, it is just long.

        return FlatMapUtil.flatMapMonoWithNull(

                () -> Mono.just(clientCode),

                key -> cacheService.get(this.getCacheName(appCode + "_" + CACHE_NAME_PROPERTIES, appCode), key)
                        .map(this.pojoClass::cast),

                (key, cApp) -> {
                    if (cApp != null)
                        return Mono.just(cApp);

                    return SecurityContextUtil.getUsersContextAuthentication()
                            .flatMap(ca -> this.readIfExistsInBase(name, appCode, ca.getUrlClientCode(),
                                    clientCode));
                },

                (key, cApp, dbApp) -> dbApp == null ? Mono.empty() : this.readInternal(dbApp.getId()),

                (key, cApp, dbApp, mergedApp) -> {

                    if (cApp == null && mergedApp == null)
                        return Mono.empty();

                    try {
                        return Mono.just(this.pojoClass.getConstructor(this.pojoClass)
                                .newInstance(cApp != null ? cApp : mergedApp));
                    } catch (Exception e) {

                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                                AbstractMongoMessageResourceService.UNABLE_TO_CREATE_OBJECT, this.getObjectName());
                    }
                },

                (key, cApp, dbApp, mergedApp, clonedApp) -> {

                    if (clonedApp == null)
                        return Mono.empty();

                    if (cApp == null && mergedApp != null) {
                        return cacheService
                                .put(this.getCacheName(appCode + "_" + CACHE_NAME_PROPERTIES, appCode), mergedApp,
                                        key)
                                .map(e -> clonedApp.getProperties());
                    }

                    return Mono.justOrEmpty(clonedApp.getProperties());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.readProperties"))
                .defaultIfEmpty(Map.of());
    }

    @Override
    public Mono<Boolean> deleteEverything(String appCode, String clientCode) {
        return super.deleteEverything(appCode, clientCode)
                .flatMap(x -> this.evictAll(appCode));
    }

    @SuppressWarnings({ "unchecked", "raw" })
    @Override
    protected Mono<ObjectWithUniqueID<Application>> applyChange(String name, String appCode, String clientCode,
            Application object, String id) {

        if (object == null)
            return Mono.empty();

        return FlatMapUtil.flatMapMonoWithNull(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.just(ca == null || object.getPermission() == null
                        || SecurityContextUtil.hasAuthority(object.getPermission(), ca.getAuthorities())),

                (ca, showShellPage) -> {

                    Map<String, Object> props = object.getProperties();

                    if (props == null || props.get("shellPage") == null)
                        return Mono.empty();

                    Object pageName = props.get("shellPage");

                    if (!BooleanUtil.safeValueOf(showShellPage) && props.get("forbiddenPage") != null)
                        pageName = props.get("forbiddenPage");

                    return this.pageService.read(pageName.toString(), object.getAppCode(), clientCode);
                },

                (ca, ssp, shellPage) -> this.fillerService.read(object.getAppCode(), object.getAppCode(), clientCode),

                (ca, ssp, shellPage, filler) -> {

                    if (object.getProperties().get("mobileApps") != null) {
                        object.getProperties().remove("mobileApps");
                    }

                    if (object.getProperties().get("manifest") != null) {
                        object.getProperties().remove("manifest");
                    }

                    if (object.getProperties().get("sso") instanceof Map<?, ?> sso) {

                        String url = StringUtil.safeValueOf(sso.get("redirectURL"));
                        if (url != null) {
                            ((Map<String, String>) sso).put("redirectURL", processForVariables(url));
                        }
                    }

                    if (shellPage == null) {

                        if (filler == null)
                            return Mono.just(new ObjectWithUniqueID<>(object, id));

                        object.getProperties()
                                .put("fillerValues", filler.getObject().getValues());
                        return Mono.just(
                                new ObjectWithUniqueID<>(object, id + filler.getUniqueId()));
                    }

                    StringBuilder sb = new StringBuilder(id);

                    if (filler != null) {
                        sb.append(filler.getUniqueId());
                        object.getProperties()
                                .put("fillerValues", filler.getObject().getValues());
                    }

                    sb.append(shellPage.getUniqueId());
                    object.getProperties()
                            .put("shellPageDefinition", shellPage.getObject());

                    return Mono.just(
                            new ObjectWithUniqueID<>(object, sb.toString()));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.applyChange"));
    }

    public String processForVariables(String url) {
        if (StringUtil.safeIsBlank(url)) {
            return url;
        }

        url = url.replace("{envDotPrefix}", appCodeSuffix);

        String env = StringUtil.safeIsBlank(appCodeSuffix) ? ""
                : appCodeSuffix.substring(appCodeSuffix.indexOf(".") + 1);

        url = url.replace("{env}", env);

        env = StringUtil.safeIsBlank(env) ? "" : env + ".";

        url = url.replace("{envDotSuffix}", env);

        return url;
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_MobileApp_CREATE')")
    public Mono<Boolean> deleteMobileApp(String id) {

        return flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.mobileAppService.readMobileApp(id),

                (ca, mobileApp) -> this.securityService
                        .hasWriteAccess(mobileApp.getAppCode(), mobileApp.getClientCode())
                        .filter(BooleanUtil::safeValueOf),

                (ca, mobileApp, hasAccess) -> this.securityService.isBeingManaged(ca.getClientCode(),
                        mobileApp.getClientCode()),

                (ca, mobileApp, hasAccess, beingManaged) -> this.mobileAppService.deleteMobileApp(id))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                UIMessageResourceService.UNABLE_TO_DELETE, "Mobile Application", id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.deleteMobileApp"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_MobileApp_CREATE')")
    public Mono<List<MobileApp>> listMobileApps(String appCode, String clientCode) {

        return flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> clientCode == null ? Mono.just(true)
                        : this.securityService.isBeingManaged(ca.getClientCode(), clientCode),

                (ca, hasAccess) -> {

                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION,
                                "Authorities.Application_CREATE");

                    return this.mobileAppService.list(appCode,
                            CommonsUtil.nonNullValue(ca.getClientCode(), clientCode));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.listMobileApps"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_MobileApp_CREATE')")
    public Mono<MobileApp> updateMobileApp(MobileApp mobileApp) {

        return flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> mobileApp.getClientCode() == null ? Mono.just(true)
                        : this.securityService.isBeingManaged(ca.getClientCode(), mobileApp.getClientCode()),

                (ca, hasAccess) -> {

                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION,
                                "Authorities.Application_CREATE");

                    if (mobileApp.getClientCode() == null)
                        mobileApp.setClientCode(ca.getClientCode());

                    mobileApp.setStatus(MobileApp.Status.PENDING);

                    if (mobileApp.getId() == null) {
                        mobileApp.setCreatedAt(LocalDateTime.now());
                        mobileApp.setCreatedBy(ca.getUser().getId().toString());
                        mobileApp.setUpdatedAt(mobileApp.getCreatedAt());
                    } else {
                        mobileApp.setUpdatedAt(LocalDateTime.now());
                    }

                    if (mobileApp.getDetails() == null) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                UIMessageResourceService.MOBILE_APP_BAD_REQUEST);
                    }

                    if (StringUtil.safeIsBlank(mobileApp.getDetails().getStartURL())) {
                        String prefix = "";
                        if (!StringUtil.safeIsBlank(appCodeSuffix)) {
                            prefix = appCodeSuffix.replace(".", "").trim();
                            if (!prefix.isBlank())
                                prefix += ".";
                        }
                        mobileApp.getDetails().setStartURL("https://" + prefix + "modlix.com/" + mobileApp.getAppCode()
                                + "/" + mobileApp.getClientCode() + "/page/");
                    }

                    mobileApp.setUpdatedBy(ca.getUser().getId().toString());

                    return this.mobileAppService.update(mobileApp);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.generateMobileApp"));

    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_MobileApp_CREATE')")
    public Mono<MobileApp> findNextAppToGenerate() {

        return flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (ca.isSystemClient())
                        return this.mobileAppService.getNextMobileAppToGenerate();

                    return this.messageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            UIMessageResourceService.INTERNAL_ONLY);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.findNextAppToGenerate"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_MobileApp_CREATE')")
    public Mono<Boolean> updateStatus(String id, MobileAppStatusUpdateRequest request) {

        return flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (ca.isSystemClient())
                        return this.mobileAppService.updateStatus(id, request);

                    return this.messageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            UIMessageResourceService.INTERNAL_ONLY);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.findNextAppToGenerate"));
    }

}
