package com.fincity.security.dao.appregistration;

import static com.fincity.security.jooq.tables.SecurityAppRegAccess.SECURITY_APP_REG_ACCESS;
import static com.fincity.security.jooq.tables.SecurityAppRegFileAccess.SECURITY_APP_REG_FILE_ACCESS;
import static com.fincity.security.jooq.tables.SecurityAppRegPackage.SECURITY_APP_REG_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityAppRegUserRole.SECURITY_APP_REG_USER_ROLE;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppRegistrationAccess;
import com.fincity.security.dto.AppRegistrationFile;
import com.fincity.security.dto.AppRegistrationPackage;
import com.fincity.security.dto.AppRegistrationRole;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityAppRegFileAccessResourceType;
import com.fincity.security.jooq.tables.SecurityPackage;
import com.fincity.security.jooq.tables.SecurityRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AppRegistrationDAO {

    private DSLContext dslContext;

    public AppRegistrationDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<Boolean> deleteEverythingRelated(ULong id) {

        return FlatMapUtil.flatMapMono(

                () -> Mono
                        .from(this.dslContext.deleteFrom(SECURITY_APP_REG_ACCESS)
                                .where(SECURITY_APP_REG_ACCESS.APP_ID.eq(id))),

                acc -> Mono.from(
                        this.dslContext.deleteFrom(SECURITY_APP_REG_PACKAGE)
                                .where(SECURITY_APP_REG_PACKAGE.APP_ID.eq(id))),

                (acc, pack) -> Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_USER_ROLE)
                        .where(SECURITY_APP_REG_USER_ROLE.APP_ID.eq(id))),

                (acc, pack, role) -> Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_FILE_ACCESS)
                        .where(SECURITY_APP_REG_FILE_ACCESS.APP_ID.eq(id))).map(e -> true)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "AppRegistrationDAO.deleteEverythingRelated"));
    }

    public Mono<AppRegistrationAccess> createAccess(App app, AppRegistrationAccess access) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.from(this.dslContext.insertInto(SECURITY_APP_REG_ACCESS)
                        .set(SECURITY_APP_REG_ACCESS.APP_ID, app.getId())
                        .set(SECURITY_APP_REG_ACCESS.CLIENT_ID,
                                CommonsUtil.nonNullValue(access.getClientId(),
                                        ULong.valueOf(ca.getUser()
                                                .getClientId())))
                        .set(SECURITY_APP_REG_ACCESS.BUSINESS_TYPE, access.getBusinessType())
                        .set(SECURITY_APP_REG_ACCESS.CLIENT_TYPE, access.getClientType())
                        .set(SECURITY_APP_REG_ACCESS.CREATED_BY,
                                ULong.valueOf(ca.getUser().getId()))
                        .set(SECURITY_APP_REG_ACCESS.LEVEL,
                                access.getLevel().toAppAccessLevel())

                        .set(SECURITY_APP_REG_ACCESS.ALLOW_APP_ID, access.getAllowAppId())
                        .returningResult(SECURITY_APP_REG_ACCESS.ID)).map(Record1::value1),

                (ca, id) -> this.getAccessById(id)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.createAccess"));
    }

    public Mono<AppRegistrationAccess> getAccessById(ULong id) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_REG_ACCESS)
                .where(SECURITY_APP_REG_ACCESS.ID.eq(id)))
                .map(e -> e.into(AppRegistrationAccess.class));
    }

    public Mono<Boolean> deleteAccess(ULong id) {
        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_ACCESS)
                .where(SECURITY_APP_REG_ACCESS.ID.eq(id)))
                .map(e -> e > 0);
    }

    public Mono<Page<AppRegistrationAccess>> getAccess(ULong appId, ULong clientId,
            String clientType, ClientLevelType level, String businessType, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        if (appId != null)
            conditions.add(SECURITY_APP_REG_ACCESS.APP_ID.eq(appId));

        if (clientId != null)
            conditions.add(SECURITY_APP_REG_ACCESS.CLIENT_ID.eq(clientId));

        if (clientType != null)
            conditions.add(SECURITY_APP_REG_ACCESS.CLIENT_TYPE.eq(clientType));

        if (level != null)
            conditions.add(SECURITY_APP_REG_ACCESS.LEVEL.eq(level.toAppAccessLevel()));

        if (businessType != null)
            conditions.add(SECURITY_APP_REG_ACCESS.BUSINESS_TYPE.eq(businessType));

        Condition condition = DSL.and(conditions);

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.selectFrom(SECURITY_APP_REG_ACCESS)
                        .where(condition)
                        .orderBy(SECURITY_APP_REG_ACCESS.CREATED_AT.desc())
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset()))
                        .map(e -> e.into(AppRegistrationAccess.class))
                        .collectList(),

                lst -> Mono
                        .from(this.dslContext.selectCount().from(SECURITY_APP_REG_ACCESS).where(condition))
                        .map(Record1::value1).map(e -> (long) e),

                (lst, count) -> {
                    if (lst.isEmpty())
                        return Mono.just(Page.<AppRegistrationAccess>empty(pageable));

                    return Mono.just(PageableExecutionUtils.<AppRegistrationAccess>getPage(lst, pageable, () -> count));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.getAccess"));
    }

    public Mono<AppRegistrationFile> createFile(App app, AppRegistrationFile file) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.from(this.dslContext.insertInto(SECURITY_APP_REG_FILE_ACCESS)
                        .set(SECURITY_APP_REG_FILE_ACCESS.APP_ID, app.getId())
                        .set(SECURITY_APP_REG_FILE_ACCESS.CLIENT_ID,
                                CommonsUtil.nonNullValue(file.getClientId(),
                                        ULong.valueOf(ca.getUser()
                                                .getClientId())))
                        .set(SECURITY_APP_REG_FILE_ACCESS.BUSINESS_TYPE, file.getBusinessType())
                        .set(SECURITY_APP_REG_FILE_ACCESS.CLIENT_TYPE, file.getClientType())
                        .set(SECURITY_APP_REG_FILE_ACCESS.CREATED_BY,
                                ULong.valueOf(ca.getUser().getId()))
                        .set(SECURITY_APP_REG_FILE_ACCESS.LEVEL,
                                file.getLevel().toFileAccessLevel())

                        .set(SECURITY_APP_REG_FILE_ACCESS.RESOURCE_TYPE,
                                "SECURED".equals(file.getResourceType()) ? SecurityAppRegFileAccessResourceType.SECURED
                                        : SecurityAppRegFileAccessResourceType.STATIC)
                        .set(SECURITY_APP_REG_FILE_ACCESS.ACCESS_NAME, file.getAccessName())
                        .set(SECURITY_APP_REG_FILE_ACCESS.WRITE_ACCESS, ByteUtil.toByte(file.isWriteAccess()))
                        .set(SECURITY_APP_REG_FILE_ACCESS.PATH, file.getPath())
                        .set(SECURITY_APP_REG_FILE_ACCESS.ALLOW_SUB_PATH_ACCESS,
                                ByteUtil.toByte(file.isAllowSubPathAccess()))

                        .returningResult(SECURITY_APP_REG_FILE_ACCESS.ID)).map(Record1::value1),

                (ca, id) -> this.getFileById(id)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.createFile"));
    }

    public Mono<AppRegistrationFile> getFileById(ULong id) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_REG_FILE_ACCESS)
                .where(SECURITY_APP_REG_FILE_ACCESS.ID.eq(id)))
                .map(e -> e.into(AppRegistrationFile.class));
    }

    public Mono<Boolean> deleteFile(ULong id) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_FILE_ACCESS)
                .where(SECURITY_APP_REG_FILE_ACCESS.ID.eq(id)))
                .map(e -> e > 0);
    }

    public Mono<Page<AppRegistrationFile>> getFile(ULong appId, ULong clientId, String clientType,
            ClientLevelType level, String businessType, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        if (appId != null)
            conditions.add(SECURITY_APP_REG_FILE_ACCESS.APP_ID.eq(appId));

        if (clientId != null)
            conditions.add(SECURITY_APP_REG_FILE_ACCESS.CLIENT_ID.eq(clientId));

        if (clientType != null)
            conditions.add(SECURITY_APP_REG_FILE_ACCESS.CLIENT_TYPE.eq(clientType));

        if (level != null)
            conditions.add(SECURITY_APP_REG_FILE_ACCESS.LEVEL.eq(level.toFileAccessLevel()));

        if (businessType != null)
            conditions.add(SECURITY_APP_REG_FILE_ACCESS.BUSINESS_TYPE.eq(businessType));

        Condition condition = DSL.and(conditions);

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.selectFrom(SECURITY_APP_REG_FILE_ACCESS)
                        .where(condition)
                        .orderBy(SECURITY_APP_REG_FILE_ACCESS.CREATED_AT.desc())
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset()))
                        .map(e -> e.into(AppRegistrationFile.class))
                        .collectList(),

                lst -> Mono
                        .from(this.dslContext.selectCount().from(SECURITY_APP_REG_FILE_ACCESS).where(condition))
                        .map(Record1::value1).map(e -> (long) e),

                (lst, count) -> {
                    if (lst.isEmpty())
                        return Mono.just(Page.<AppRegistrationFile>empty(pageable));

                    return Mono.just(PageableExecutionUtils.<AppRegistrationFile>getPage(lst, pageable, () -> count));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.getFile"));
    }

    public Mono<AppRegistrationPackage> createPackage(App app, AppRegistrationPackage regPackage) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.from(this.dslContext.insertInto(SECURITY_APP_REG_PACKAGE)
                        .set(SECURITY_APP_REG_PACKAGE.APP_ID, app.getId())
                        .set(SECURITY_APP_REG_PACKAGE.CLIENT_ID,
                                CommonsUtil.nonNullValue(regPackage.getClientId(),
                                        ULong.valueOf(ca.getUser()
                                                .getClientId())))
                        .set(SECURITY_APP_REG_PACKAGE.BUSINESS_TYPE, regPackage.getBusinessType())
                        .set(SECURITY_APP_REG_PACKAGE.CLIENT_TYPE, regPackage.getClientType())
                        .set(SECURITY_APP_REG_PACKAGE.CREATED_BY,
                                ULong.valueOf(ca.getUser().getId()))
                        .set(SECURITY_APP_REG_PACKAGE.LEVEL,
                                regPackage.getLevel().toPackageLevel())

                        .set(SECURITY_APP_REG_PACKAGE.PACKAGE_ID, regPackage.getPackageId())
                        .returningResult(SECURITY_APP_REG_PACKAGE.ID)).map(Record1::value1),

                (ca, id) -> this.getPackageById(id)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.createPackage"));
    }

    public Mono<AppRegistrationPackage> getPackageById(ULong id) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_REG_PACKAGE)
                .where(SECURITY_APP_REG_PACKAGE.ID.eq(id)))
                .map(e -> e.into(AppRegistrationPackage.class));
    }

    public Mono<Boolean> deletePackage(ULong id) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_PACKAGE)
                .where(SECURITY_APP_REG_PACKAGE.ID.eq(id)))
                .map(e -> e > 0);
    }

    public Mono<Page<AppRegistrationPackage>> getPackage(ULong appId, String packageName, ULong clientId,
            String clientType, ClientLevelType level, String businessType, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        if (appId != null)
            conditions.add(SECURITY_APP_REG_PACKAGE.APP_ID.eq(appId));

        if (packageName != null)
            conditions.add(SecurityPackage.SECURITY_PACKAGE.NAME.like("%" + packageName + "%"));

        if (clientId != null)
            conditions.add(SECURITY_APP_REG_PACKAGE.CLIENT_ID.eq(clientId));

        if (clientType != null)
            conditions.add(SECURITY_APP_REG_PACKAGE.CLIENT_TYPE.eq(clientType));

        if (level != null)
            conditions.add(SECURITY_APP_REG_PACKAGE.LEVEL.eq(level.toPackageLevel()));

        if (businessType != null)
            conditions.add(SECURITY_APP_REG_PACKAGE.BUSINESS_TYPE.eq(businessType));

        Condition condition = DSL.and(conditions);

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.select(SECURITY_APP_REG_PACKAGE.fields())
                        .from(SECURITY_APP_REG_PACKAGE)
                        .leftJoin(SecurityPackage.SECURITY_PACKAGE).on(SecurityPackage.SECURITY_PACKAGE.ID
                                .eq(SECURITY_APP_REG_PACKAGE.PACKAGE_ID))
                        .where(condition)

                        .orderBy(SECURITY_APP_REG_PACKAGE.CREATED_AT.desc())
                        .limit(pageable.getPageSize()).offset(pageable.getOffset()))

                        .map(e -> e.into(AppRegistrationPackage.class))
                        .collectList(),

                lst -> Mono
                        .from(this.dslContext.selectCount().from(SECURITY_APP_REG_PACKAGE).where(condition))
                        .map(Record1::value1).map(e -> (long) e),

                (lst, count) -> {
                    if (lst.isEmpty())
                        return Mono.just(Page.<AppRegistrationPackage>empty(pageable));

                    return Mono
                            .just(PageableExecutionUtils.<AppRegistrationPackage>getPage(lst, pageable, () -> count));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.getPackage"));
    }

    public Mono<AppRegistrationRole> createRole(App app, AppRegistrationRole role) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.from(this.dslContext.insertInto(SECURITY_APP_REG_USER_ROLE)
                        .set(SECURITY_APP_REG_USER_ROLE.APP_ID, app.getId())
                        .set(SECURITY_APP_REG_USER_ROLE.CLIENT_ID,
                                CommonsUtil.nonNullValue(role.getClientId(),
                                        ULong.valueOf(ca.getUser()
                                                .getClientId())))
                        .set(SECURITY_APP_REG_USER_ROLE.BUSINESS_TYPE, role.getBusinessType())
                        .set(SECURITY_APP_REG_USER_ROLE.CLIENT_TYPE, role.getClientType())
                        .set(SECURITY_APP_REG_USER_ROLE.CREATED_BY,
                                ULong.valueOf(ca.getUser().getId()))
                        .set(SECURITY_APP_REG_USER_ROLE.LEVEL,
                                role.getLevel().toUserRoleLevel())

                        .set(SECURITY_APP_REG_USER_ROLE.ROLE_ID, role.getRoleId())
                        .returningResult(SECURITY_APP_REG_USER_ROLE.ID)).map(Record1::value1),

                (ca, id) -> this.getRoleById(id)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.createRole"));
    }

    public Mono<AppRegistrationRole> getRoleById(ULong id) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_REG_USER_ROLE)
                .where(SECURITY_APP_REG_USER_ROLE.ID.eq(id)))
                .map(e -> e.into(AppRegistrationRole.class));
    }

    public Mono<Boolean> deleteRole(ULong id) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_REG_USER_ROLE)
                .where(SECURITY_APP_REG_USER_ROLE.ID.eq(id)))
                .map(e -> e > 0);
    }

    public Mono<Page<AppRegistrationRole>> getRole(ULong appId, String roleName, ULong clientId,
            String clientType, ClientLevelType level, String businessType, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        if (appId != null)
            conditions.add(SECURITY_APP_REG_USER_ROLE.APP_ID.eq(appId));

        if (roleName != null)
            conditions.add(SecurityRole.SECURITY_ROLE.NAME.like("%" + roleName + "%"));

        if (clientId != null)
            conditions.add(SECURITY_APP_REG_USER_ROLE.CLIENT_ID.eq(clientId));

        if (clientType != null)
            conditions.add(SECURITY_APP_REG_USER_ROLE.CLIENT_TYPE.eq(clientType));

        if (level != null)
            conditions.add(SECURITY_APP_REG_USER_ROLE.LEVEL.eq(level.toUserRoleLevel()));

        if (businessType != null)
            conditions.add(SECURITY_APP_REG_USER_ROLE.BUSINESS_TYPE.eq(businessType));

        Condition condition = DSL.and(conditions);

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.select(SECURITY_APP_REG_USER_ROLE.fields())
                        .from(SECURITY_APP_REG_USER_ROLE)
                        .leftJoin(SecurityRole.SECURITY_ROLE).on(SecurityRole.SECURITY_ROLE.ID
                                .eq(SECURITY_APP_REG_USER_ROLE.ROLE_ID))
                        .where(condition)

                        .orderBy(SECURITY_APP_REG_USER_ROLE.CREATED_AT.desc())
                        .limit(pageable.getPageSize()).offset(pageable.getOffset()))

                        .map(e -> e.into(AppRegistrationRole.class))
                        .collectList(),

                lst -> Mono
                        .from(this.dslContext.selectCount().from(SECURITY_APP_REG_USER_ROLE).where(condition))
                        .map(Record1::value1).map(e -> (long) e),

                (lst, count) -> {
                    if (lst.isEmpty())
                        return Mono.just(Page.<AppRegistrationRole>empty(pageable));

                    return Mono
                            .just(PageableExecutionUtils.<AppRegistrationRole>getPage(lst, pageable, () -> count));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationDAO.getRole"));
    }
}
