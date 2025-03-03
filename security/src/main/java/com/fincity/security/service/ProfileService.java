package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

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

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;

    public ProfileService(SecurityMessageResourceService securityMessageResourceService, ClientService clientService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_CREATE')")
    @Override
    public Mono<Profile> create(Profile entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getClientId() == null)
                        return Mono.just(entity.setClientId(ULong.valueOf(ca.getUser().getClientId())));

                    if (ca.isSystemClient())
                        return Mono.just(entity);

                    return this.clientService
                            .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
                            .filter(BooleanUtil::safeValueOf)
                            .map(x -> entity);
                },

                (ca, managed) -> super.create(entity)

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
        return super.read(id);
    }

    public Mono<Profile> readInternal(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ')")
    @Override
    public Mono<Page<Profile>> readPageFilter(Pageable pageable, AbstractCondition cond) {
        return super.readPageFilter(pageable, cond);

    }

    @PreAuthorize("hasAuthority('Authorities.Role_UPDATE')")
    @Override
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

                ca -> this.read(id),

                (ca, existing) -> super.delete(id)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.delete"))
                .onErrorResume(
                        ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
                                ? this.securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg, ex),
                                        SecurityMessageResourceService.DELETE_ROLE_ERROR)
                                : Mono.error(ex));
    }
}
