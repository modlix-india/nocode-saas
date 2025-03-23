package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.exception.DataAccessException;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ProfileDAO;
import com.fincity.security.dto.Profile;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProfileService
        extends AbstractSecurityUpdatableDataService<SecurityProfileRecord, ULong, Profile, ProfileDAO> {

    private static final String PROFILE = "Profile";

    private static final String DESCRIPTION = "description";
    private static final String NAME = "name";

    private static final String CACHE_NAME_APP_ID_PROFILE = "appIdProfile";

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;
    private final ClientHierarchyService clientHierarchyService;
    private final AppService appService;
    private final CacheService cacheService;

    public ProfileService(SecurityMessageResourceService securityMessageResourceService, ClientService clientService,
            ClientHierarchyService clientHierarchyService, CacheService cacheService, AppService appService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
        this.cacheService = cacheService;
        this.appService = appService;
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_CREATE')")
    @Override
    public Mono<Profile> create(Profile entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getAppId() == null)
                        return this.securityMessageResourceService.<Boolean>throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.PROFILE_NEEDS_APP);

                    // Checking whether the profile's client id has access to the app or not.
                    return this.appService.hasReadAccess(entity.getAppId(), entity.getClientId())
                            .filter(BooleanUtil::safeValueOf);
                },

                (ca, hasAppAccess) -> {

                    if (ca.isSystemClient())
                        return Mono.just(true);

                    return this.appService.hasReadAccess(entity.getAppId(), ULong.valueOf(ca.getUser().getClientId()))
                            .filter(BooleanUtil::safeValueOf);
                },

                (ca, hasAppAccess, clientAlsoHasAppAccess) -> {

                    if (entity.getClientId() == null)
                        return Mono.just(entity.setClientId(ULong.valueOf(ca.getUser().getClientId())));

                    if (ca.isSystemClient())
                        return Mono.just(entity);

                    return this.clientService
                            .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
                            .filter(BooleanUtil::safeValueOf)
                            .map(x -> entity);
                },

                (ca, hasAppAccess, clientAlsoHasAppAccess, managed) -> clientHierarchyService
                        .getClientHierarchy(entity.getClientId()),

                (ca, hasAppAccess, clientAlsoHasAppAccess, managed,
                        clientHierarchy) -> this.dao.hasAccessToRoles(entity.getAppId(), clientHierarchy,
                                entity),

                (ca, hasAppAccess, clientAlsoHasAppAccess, managed, clientHierarchy, hasAccessToRoles) -> {
                    if (!hasAccessToRoles) {
                        return this.securityMessageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.FORBIDDEN_ROLE_ACCESS);
                    }
                    entity.setCreatedBy(ULong.valueOf(ca.getUser().getId()));
                    return this.dao.create(entity, clientHierarchy);
                }

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.create"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService
                        .getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                                StringFormatter.format(msg, PROFILE))))));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ')")
    @Override
    public Mono<Profile> read(ULong id) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, clientHierarchy) -> this.dao.read(id, clientHierarchy))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.read"));
    }

    public Mono<Profile> readInternal(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ')")
    public Mono<Page<Profile>> readAll(ULong appId, Pageable pageable) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, clientHierarchy) -> this.dao.readAll(appId, clientHierarchy, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.readAll"));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_UPDATE')")
    public Mono<Profile> update(Profile entity) {
        return this.dao.canBeUpdated(entity.getId())
                .filter(BooleanUtil::safeValueOf)
                .flatMap(x -> super.update(entity))
                .switchIfEmpty(Mono.defer(
                        () -> securityMessageResourceService.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
                                .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
                                        StringFormatter.format(msg, PROFILE, entity.getId()))))));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_UPDATE')")
    @Override
    public Mono<Profile> update(ULong id, Map<String, Object> fields) {
        return this.dao.canBeUpdated(id)
                .filter(BooleanUtil::safeValueOf)
                .flatMap(x -> super.update(id, fields))
                .switchIfEmpty(Mono.defer(
                        () -> securityMessageResourceService.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
                                .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
                                        StringFormatter.format(msg, PROFILE, id))))));
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.PROFILE;
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        Map<String, Object> newFields = new HashMap<>();

        if (fields.containsKey(NAME))
            newFields.put(NAME, fields.get(NAME));
        if (fields.containsKey(DESCRIPTION))
            newFields.put(DESCRIPTION, fields.get(DESCRIPTION));

        return Mono.just(newFields);
    }

    @Override
    protected Mono<Profile> updatableEntity(Profile entity) {
        return this.read(entity.getId())
                .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
                        .map(ca -> {
                            existing.setDescription(entity.getDescription());
                            existing.setName(entity.getName());
                            return existing;
                        }));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, hierarchy) -> this.dao.delete(id, hierarchy)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.delete"))
                .onErrorResume(
                        ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
                                ? this.securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg, ex),
                                        SecurityMessageResourceService.DELETE_ROLE_ERROR)
                                : Mono.error(ex));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ') and hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> restrictClient(ULong profileId, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
                        .filter(BooleanUtil::safeValueOf),

                (ca, managed) -> this.dao.checkProfileAppAccess(profileId, clientId).filter(BooleanUtil::safeValueOf),

                (ca, managed, hasAppAccess) -> this.dao.restrictClient(profileId, clientId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.restrictClient"));
    }
}
