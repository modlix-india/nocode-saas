package com.fincity.saas.files.service;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.dao.FilesAccessPathDao;
import com.fincity.saas.files.dto.FilesAccessPath;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class FilesAccessPathService
        extends AbstractJOOQUpdatableDataService<FilesAccessPathRecord, ULong, FilesAccessPath, FilesAccessPathDao> {

    private static final String RESOURCE_TYPE = "resourceType";
    private static final String PATH = "path";
    private static final String WRITE_ACCESS = "writeAccess";
    private static final String ALLOW_SUB_PATH_ACCESS = "allowSubPathAccess";
    private static final String USER_ID = "userId";
    private static final String ACCESS_NAME = "accessName";

    @Autowired
    private FilesMessageResourceService msgService;

    @Autowired
    private FeignAuthenticationService securityService;

    @Override
    public Mono<FilesAccessPath> create(FilesAccessPath entity) {

        if (entity.getAccessName() != null) {
            entity.setUserId(null);
        } else if (entity.getUserId() != null) {
            entity.setAccessName(null);
        } else {
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
        }

        if (entity.getResourceType() == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.MISSING_FIELD, "Resource Type");

        entity.setPath(entity.getPath() == null || entity.getPath()
                .isBlank() ? "/"
                : entity.getPath()
                .trim());

        return this.checkAccessNGetClientCode(entity.getResourceType()
                        .toString())
                .flatMap(v -> super.create(entity.setClientCode(v)));
    }

    @Override
    public Mono<FilesAccessPath> read(ULong id) {

        return FlatMapUtil.flatMapMono(

                        () -> super.read(id),

                        e -> this.checkAccessNGetClientCode(e.getResourceType()
                                .toString(), e.getClientCode()),

                        (e, clientCode) -> Mono.just(e))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.read"));
    }

    @Override
    protected Mono<FilesAccessPath> updatableEntity(FilesAccessPath entity) {

        return FlatMapUtil.flatMapMono(

                        () -> this.dao.readById(entity.getId()),

                        e -> this.checkAccessNGetClientCode(e.getResourceType()
                                .toString(), entity.getClientCode()),

                        (e, clientCode) -> {
                            if (entity.getAccessName() == null) {
                                e.setUserId(entity.getUserId());
                                e.setAccessName(null);
                            } else if (entity.getUserId() == null) {
                                e.setAccessName(entity.getAccessName());
                                e.setUserId(null);
                            } else {
                                msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
                            }

                            e.setAllowSubPathAccess(entity.isAllowSubPathAccess());
                            e.setPath(entity.getPath() == null || entity.getPath()
                                    .isBlank() ? "/"
                                    : entity.getPath()
                                    .trim());
                            e.setWriteAccess(entity.isWriteAccess());

                            return Mono.just(e);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.updatableEntity"));
    }

    @Override
    public Mono<Page<FilesAccessPath>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(

                        () -> condition.findConditionWithField(RESOURCE_TYPE)
                                .collectList(),

                        cs -> SecurityContextUtil.getUsersContextAuthentication(),

                        (cs, ca) -> prepareConditionForRead(condition, cs, ca),

                        (cs, ca, newCondition) -> super.readPageFilter(pageable,
                                new ComplexCondition()
                                        .setConditions(List.of(newCondition, new FilterCondition().setField("clientCode")
                                                .setValue(ca.getLoggedInFromClientCode())))
                                        .setOperator(ComplexConditionOperator.AND)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.readPageFilter"));
    }

    private Mono<AbstractCondition> prepareConditionForRead(AbstractCondition condition, List<FilterCondition> cs,
                                                            ContextAuthentication ca) {

        if (!ca.isSystemClient() && !ca.getLoggedInFromClientId()
                .equals(ca.getUser()
                        .getClientId())) {

            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PERMISSION, "");
        }

        boolean hasStatic = SecurityContextUtil
                .hasAuthority(this.getAuthority(FilesAccessPathResourceType.STATIC.toString()), ca.getAuthorities());
        boolean hasSecured = SecurityContextUtil
                .hasAuthority(this.getAuthority(FilesAccessPathResourceType.SECURED.toString()), ca.getAuthorities());

        if (cs.isEmpty()) {

            return processConditionWhenNoConditionsInRequest(condition, hasStatic, hasSecured);
        }

        List<String> list = cs.stream()
                .filter(FilterCondition.class::isInstance)
                .map(e -> e.getValue())
                .map(Object::toString)
                .distinct()
                .toList();

        for (String rtype : list) {

            if (rtype.contains(FilesAccessPathResourceType.STATIC.toString()) && !hasStatic) {
                return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        FilesMessageResourceService.FORBIDDEN_PERMISSION, "STATIC Files PATH");
            }

            if (rtype.contains(FilesAccessPathResourceType.SECURED.toString()) && !hasSecured) {
                return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        FilesMessageResourceService.FORBIDDEN_PERMISSION, "SECURED Files PATH");
            }
        }

        return Mono.just(condition);
    }

    private Mono<AbstractCondition> processConditionWhenNoConditionsInRequest(AbstractCondition condition,
                                                                              boolean hasStatic, boolean hasSecured) {
        if (!hasSecured && !hasStatic)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PERMISSION, "STATIC Files PATH / SECURED Files PATH");

        if (hasSecured && hasStatic)
            return Mono.just(condition);

        if (hasSecured)
            return Mono.just(new ComplexCondition()
                    .setConditions(List.of(condition, new FilterCondition().setField(RESOURCE_TYPE)
                            .setValue(FilesAccessPathResourceType.SECURED.toString())))
                    .setOperator(ComplexConditionOperator.AND));

        return Mono.just(new ComplexCondition()
                .setConditions(List.of(condition, new FilterCondition().setField(RESOURCE_TYPE)
                        .setValue(FilesAccessPathResourceType.STATIC.toString())))
                .setOperator(ComplexConditionOperator.AND));
    }

    public Mono<String> checkAccessNGetClientCode(String resourceType) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.checkAccessNGetClientCode(resourceType, ca.getLoggedInFromClientCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.checkAccessNGetClientCode"));
    }

    public Mono<String> checkAccessNGetClientCode(String resourceType, String clientCode) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> {
                            if (ca.isSystemClient() || ca.getLoggedInFromClientId()
                                    .equals(ca.getUser()
                                            .getClientId()))
                                return Mono.just(true);

                            return securityService.isUserBeingManaged(ca.getUser()
                                    .getId(), clientCode);
                        },

                        (ca, managed) -> {

                            if (!managed.booleanValue() || !SecurityContextUtil.hasAuthority(this.getAuthority(resourceType),
                                    ca.getAuthorities())) {
                                return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        FilesMessageResourceService.FORBIDDEN_PERMISSION, this.getAuthority(resourceType));
                            }

                            return Mono.just(ca.getLoggedInFromClientCode());
                        }

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.checkAccessNGetClientCode"));

    }

    private String getAuthority(String resourceType) {
        return "Authorities." + resourceType + "_Files_PATH";
    }

    @Override
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(

                        () -> this.dao.readById(id),

                        e -> this.checkAccessNGetClientCode(e.getResourceType()
                                .toString(), e.getClientCode()),

                        (e, clientCode) -> super.delete(id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.delete"));
    }

    public Mono<Boolean> hasReadAccess(String actualPath, String clientCode, FilesAccessPathResourceType resourceType) {

        String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> ca.isSystemClient() ? Mono.just(true)
                                : this.securityService.isBeingManaged(ca.getClientCode(), clientCode),

                        (ca, managed) -> {
                            if (!managed.booleanValue())
                                return Mono.just(false);

                            return this.dao.hasPathReadAccess(path, ULong.valueOf(ca.getUser()
                                    .getId()), clientCode, resourceType, ca.getAuthorities()
                                    .stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .toList());
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.hasReadAccess"))
                .defaultIfEmpty(false);

    }

    public Mono<Boolean> hasWriteAccess(String actualPath, String clientCode,
                                        FilesAccessPathResourceType resourceType) {

        String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> ca.isSystemClient() ? this.securityService.isValidClientCode(clientCode)
                                : this.securityService.isBeingManaged(ca.getClientCode(), clientCode),

                        (ca, managed) -> {
                            if (!managed.booleanValue())
                                return Mono.empty();

                            return this.dao.hasPathWriteAccess(path, ULong.valueOf(ca.getUser()
                                    .getId()), clientCode, resourceType, ca.getAuthorities()
                                    .stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .toList());
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathService.hasWriteAccess"))
                .switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND, "Client", clientCode))
                .defaultIfEmpty(false);
    }

    public Mono<FilesAccessPath> createInternalAccessPath(FilesAccessPath accessPath) {

        if (accessPath.getAccessName() != null) {
            accessPath.setUserId(null);
        } else if (accessPath.getUserId() != null) {
            accessPath.setAccessName(null);
        } else {
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
        }

        if (accessPath.getResourceType() == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.MISSING_FIELD, "Resource Type");

        accessPath.setPath(accessPath.getPath() == null || accessPath.getPath().trim()
                .isBlank() ? ""
                : accessPath.getPath()
                .trim());

        return super.create(accessPath);
    }

    public Mono<Boolean> isClientBeingManaged(String managingClientCode, String clientCode) {

        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return Mono.just(true);

        return this.securityService.isBeingManaged(managingClientCode, clientCode);
    }
}
