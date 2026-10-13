package com.modlix.saas.files.dao;

import static com.modlix.saas.files.jooq.tables.FilesRetentionPolicy.FILES_RETENTION_POLICY;

import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyResourceType;
import com.modlix.saas.files.jooq.tables.records.FilesRetentionPolicyRecord;

/** Storage of the path retention policies. All access checks live in the service. */
@Service
public class FilesRetentionPolicyDao {

    private final DSLContext context;

    public FilesRetentionPolicyDao(DSLContext context) {
        this.context = context;
    }

    public FilesRetentionPolicy readById(ULong id) {
        return this.context.selectFrom(FILES_RETENTION_POLICY)
                .where(FILES_RETENTION_POLICY.ID.eq(id))
                .fetchOptional()
                .map(FilesRetentionPolicyDao::toDto)
                .orElse(null);
    }

    /** Every row (both levels) of an app, optionally of one resource type. */
    public List<FilesRetentionPolicy> readByApp(String appCode, FilesRetentionPolicyResourceType type) {
        return this.context.selectFrom(FILES_RETENTION_POLICY)
                .where(FILES_RETENTION_POLICY.APP_CODE.eq(appCode))
                .and(type == null ? DSL.noCondition() : FILES_RETENTION_POLICY.RESOURCE_TYPE.eq(type))
                .orderBy(FILES_RETENTION_POLICY.RESOURCE_TYPE, FILES_RETENTION_POLICY.PATH,
                        FILES_RETENTION_POLICY.LEVEL, FILES_RETENTION_POLICY.CLIENT_CODE,
                        FILES_RETENTION_POLICY.APPLIES_TO)
                .fetch()
                .map(FilesRetentionPolicyDao::toDto);
    }

    /** The registered prefixes (APP rows) of a resource type across all apps, for the sweep. */
    public List<FilesRetentionPolicy> readAppRows(FilesRetentionPolicyResourceType type) {
        return this.context.selectFrom(FILES_RETENTION_POLICY)
                .where(FILES_RETENTION_POLICY.LEVEL.eq(FilesRetentionPolicyLevel.APP))
                .and(FILES_RETENTION_POLICY.RESOURCE_TYPE.eq(type))
                .orderBy(FILES_RETENTION_POLICY.APP_CODE, FILES_RETENTION_POLICY.PATH)
                .fetch()
                .map(FilesRetentionPolicyDao::toDto);
    }

    public boolean existsAppRow(String appCode, FilesRetentionPolicyResourceType type, String path) {
        return this.context.fetchExists(FILES_RETENTION_POLICY, DSL.and(
                FILES_RETENTION_POLICY.APP_CODE.eq(appCode),
                FILES_RETENTION_POLICY.RESOURCE_TYPE.eq(type),
                FILES_RETENTION_POLICY.PATH.eq(path),
                FILES_RETENTION_POLICY.LEVEL.eq(FilesRetentionPolicyLevel.APP)));
    }

    public boolean exists(FilesRetentionPolicy p) {
        return this.context.fetchExists(FILES_RETENTION_POLICY, identity(p));
    }

    public FilesRetentionPolicy create(FilesRetentionPolicy p, ULong userId) {

        ULong id = this.context.insertInto(FILES_RETENTION_POLICY)
                .set(FILES_RETENTION_POLICY.APP_CODE, p.getAppCode())
                .set(FILES_RETENTION_POLICY.CLIENT_CODE, p.getClientCode())
                .set(FILES_RETENTION_POLICY.RESOURCE_TYPE, p.getResourceType())
                .set(FILES_RETENTION_POLICY.PATH, p.getPath())
                .set(FILES_RETENTION_POLICY.RETENTION_DAYS, toUInt(p.getRetentionDays()))
                .set(FILES_RETENTION_POLICY.APPLIES_TO, p.getAppliesTo())
                .set(FILES_RETENTION_POLICY.LEVEL, p.getLevel())
                .set(FILES_RETENTION_POLICY.CREATED_BY, userId)
                .set(FILES_RETENTION_POLICY.UPDATED_BY, userId)
                .returningResult(FILES_RETENTION_POLICY.ID)
                .fetchOneInto(ULong.class);

        return id == null ? null : this.readById(id);
    }

    /** Only the days change; a different path, client or scope is a different row. */
    public boolean updateDays(ULong id, Integer retentionDays, ULong userId) {
        return this.context.update(FILES_RETENTION_POLICY)
                .set(FILES_RETENTION_POLICY.RETENTION_DAYS, toUInt(retentionDays))
                .set(FILES_RETENTION_POLICY.UPDATED_BY, userId)
                .where(FILES_RETENTION_POLICY.ID.eq(id))
                .execute() > 0;
    }

    public int deleteById(ULong id) {
        return this.context.deleteFrom(FILES_RETENTION_POLICY).where(FILES_RETENTION_POLICY.ID.eq(id)).execute();
    }

    /**
     * Removes the client overrides of a prefix. Used when its APP row goes: a client row may only
     * tune a registered prefix, so it has no meaning once the prefix is unregistered.
     */
    public int deleteClientRowsOfPrefix(String appCode, FilesRetentionPolicyResourceType type, String path) {
        return this.context.deleteFrom(FILES_RETENTION_POLICY)
                .where(FILES_RETENTION_POLICY.APP_CODE.eq(appCode))
                .and(FILES_RETENTION_POLICY.RESOURCE_TYPE.eq(type))
                .and(FILES_RETENTION_POLICY.PATH.eq(path))
                .and(FILES_RETENTION_POLICY.LEVEL.eq(FilesRetentionPolicyLevel.CLIENT))
                .execute();
    }

    private static Condition identity(FilesRetentionPolicy p) {
        return DSL.and(
                FILES_RETENTION_POLICY.APP_CODE.eq(p.getAppCode()),
                FILES_RETENTION_POLICY.CLIENT_CODE.eq(p.getClientCode()),
                FILES_RETENTION_POLICY.RESOURCE_TYPE.eq(p.getResourceType()),
                FILES_RETENTION_POLICY.PATH.eq(p.getPath()),
                FILES_RETENTION_POLICY.APPLIES_TO.eq(
                        p.getAppliesTo() == null ? FilesRetentionPolicyAppliesTo.SELF : p.getAppliesTo()),
                FILES_RETENTION_POLICY.LEVEL.eq(p.getLevel()));
    }

    private static UInteger toUInt(Integer v) {
        return v == null ? null : UInteger.valueOf(v);
    }

    static FilesRetentionPolicy toDto(FilesRetentionPolicyRecord r) {
        FilesRetentionPolicy p = new FilesRetentionPolicy()
                .setAppCode(r.getAppCode())
                .setClientCode(r.getClientCode())
                .setResourceType(r.getResourceType())
                .setPath(r.getPath())
                .setRetentionDays(r.getRetentionDays() == null ? null : r.getRetentionDays().intValue())
                .setAppliesTo(r.getAppliesTo())
                .setLevel(r.getLevel());
        p.setId(r.getId());
        p.setCreatedAt(r.getCreatedAt());
        p.setCreatedBy(r.getCreatedBy());
        p.setUpdatedAt(r.getUpdatedAt());
        p.setUpdatedBy(r.getUpdatedBy());
        return p;
    }
}
