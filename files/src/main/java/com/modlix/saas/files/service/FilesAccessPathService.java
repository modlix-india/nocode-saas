package com.modlix.saas.files.service;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.files.dao.FilesAccessPathDao;
import com.modlix.saas.files.dto.FilesAccessPath;
import com.modlix.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.modlix.saas.files.jooq.tables.records.FilesAccessPathRecord;

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
    public FilesAccessPath create(FilesAccessPath entity) {

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

        String clientCode = this.checkAccessNGetClientCode(entity.getResourceType()
                .toString());

        return super.create(entity.setClientCode(clientCode));
    }

    @Override
    public FilesAccessPath read(ULong id) {

        FilesAccessPath accessPath = super.read(id);

        this.checkAccessNGetClientCode(accessPath.getResourceType().toString(), accessPath.getClientCode());

        return accessPath;
    }

    @Override
    protected FilesAccessPath updatableEntity(FilesAccessPath entity) {

        FilesAccessPath accessPath = this.dao.readById(entity.getId());

        this.checkAccessNGetClientCode(accessPath.getResourceType().toString(), accessPath.getClientCode());

        if (entity.getAccessName() == null) {
            accessPath.setUserId(entity.getUserId());
            accessPath.setAccessName(null);
        } else if (entity.getUserId() == null) {
            accessPath.setAccessName(entity.getAccessName());
            accessPath.setUserId(null);
        } else {
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
        }

        accessPath.setAllowSubPathAccess(entity.isAllowSubPathAccess());
        accessPath.setPath(entity.getPath() == null || entity.getPath()
                .isBlank() ? "/"
                        : entity.getPath()
                                .trim());
        return accessPath.setWriteAccess(entity.isWriteAccess());
    }

    @Override
    public Page<FilesAccessPath> readPageFilter(Pageable pageable, AbstractCondition condition) {

        List<FilterCondition> cs = condition.findConditionWithField(RESOURCE_TYPE);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        AbstractCondition newCondition = this.prepareConditionForRead(condition, cs, ca);

        return super.readPageFilter(pageable,
                new ComplexCondition()
                        .setConditions(List.of(newCondition, new FilterCondition().setField("clientCode")
                                .setValue(ca.getLoggedInFromClientCode())))
                        .setOperator(ComplexConditionOperator.AND));
    }

    private AbstractCondition prepareConditionForRead(AbstractCondition condition, List<FilterCondition> cs,
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

        for (String rType : list) {

            if (rType.contains(FilesAccessPathResourceType.STATIC.toString()) && !hasStatic) {
                return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        FilesMessageResourceService.FORBIDDEN_PERMISSION, "STATIC Files PATH");
            }

            if (rType.contains(FilesAccessPathResourceType.SECURED.toString()) && !hasSecured) {
                return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        FilesMessageResourceService.FORBIDDEN_PERMISSION, "SECURED Files PATH");
            }
        }

        return condition;
    }

    private AbstractCondition processConditionWhenNoConditionsInRequest(AbstractCondition condition,
            boolean hasStatic, boolean hasSecured) {
        if (!hasSecured && !hasStatic)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PERMISSION, "STATIC Files PATH / SECURED Files PATH");

        if (hasSecured && hasStatic)
            return condition;

        if (hasSecured)
            return new ComplexCondition()
                    .setConditions(List.of(condition, new FilterCondition().setField(RESOURCE_TYPE)
                            .setValue(FilesAccessPathResourceType.SECURED.toString())))
                    .setOperator(ComplexConditionOperator.AND);

        return new ComplexCondition()
                .setConditions(List.of(condition, new FilterCondition().setField(RESOURCE_TYPE)
                        .setValue(FilesAccessPathResourceType.STATIC.toString())))
                .setOperator(ComplexConditionOperator.AND);
    }

    public String checkAccessNGetClientCode(String resourceType) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return this.checkAccessNGetClientCode(resourceType, ca.getLoggedInFromClientCode());
    }

    public String checkAccessNGetClientCode(String resourceType, String clientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        boolean systemOrLoggedIn = ca.isSystemClient()
                || ca.getLoggedInFromClientId().equals(ca.getUser().getClientId());

        boolean managed = systemOrLoggedIn || securityService.isUserBeingManaged(ca.getUser().getId(), clientCode);

        if (!managed || !SecurityContextUtil.hasAuthority(this.getAuthority(resourceType),
                ca.getAuthorities())) {
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PERMISSION, this.getAuthority(resourceType));
        }

        return ca.getLoggedInFromClientCode();
    }

    private String getAuthority(String resourceType) {
        return "Authorities." + resourceType + "_Files_PATH";
    }

    @Override
    public Integer delete(ULong id) {

        FilesAccessPath accessPath = this.dao.readById(id);

        this.checkAccessNGetClientCode(accessPath.getResourceType().toString(), accessPath.getClientCode());

        return super.delete(id);
    }

    public boolean hasReadAccess(String actualPath, String clientCode, FilesAccessPathResourceType resourceType) {

        String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        boolean managed = ca.isSystemClient()
                || securityService.isBeingManaged(ca.getClientCode(), clientCode);

        if (!managed)
            return false;

        return this.dao.hasPathReadAccess(path, ULong.valueOf(ca.getUser()
                .getId()), clientCode, resourceType, ca.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
    }

    public boolean hasWriteAccess(String actualPath, String clientCode,
            FilesAccessPathResourceType resourceType) {

        String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        boolean managed = ca.isSystemClient() ? this.securityService.isValidClientCode(clientCode)
                : this.securityService.isBeingManaged(ca.getClientCode(), clientCode);

        if (!managed)
            return false;

        if (ca.isSystemClient() &&
                path.contains(SecuredFileResourceService.WITH_IN_CLIENT) &&
                SecurityContextUtil.hasAuthority("Authorities.ROLE_MobileApp_UPLOADER",
                        ca.getAuthorities())) {
            return true;
        }

        return this.dao.hasPathWriteAccess(path, ULong.valueOf(ca.getUser()
                .getId()), clientCode, resourceType, ca.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
    }

    public FilesAccessPath createInternalAccessPath(FilesAccessPath accessPath) {

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

    public boolean isClientBeingManaged(String managingClientCode, String clientCode) {

        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return true;

        return this.securityService.isBeingManaged(managingClientCode, clientCode);
    }
}
