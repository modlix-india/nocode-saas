package com.modlix.saas.files.dao;

import static com.modlix.saas.files.jooq.tables.FilesAccessPath.FILES_ACCESS_PATH;

import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.util.ByteUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.files.dto.FilesAccessPath;
import com.modlix.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.modlix.saas.files.jooq.tables.records.FilesAccessPathRecord;

@Service
public class FilesAccessPathDao extends AbstractUpdatableDAO<FilesAccessPathRecord, ULong, FilesAccessPath> {

    protected FilesAccessPathDao() {
        super(FilesAccessPath.class, FILES_ACCESS_PATH, FILES_ACCESS_PATH.ID);
    }

    public FilesAccessPath create(FilesAccessPath pojo) {

        FilesAccessPath existing = this.dslContext.selectFrom(this.table).where(DSL.and(
                FILES_ACCESS_PATH.PATH.eq(pojo.getPath()),
                FILES_ACCESS_PATH.CLIENT_CODE.eq(pojo.getClientCode()),
                FILES_ACCESS_PATH.RESOURCE_TYPE.eq(pojo.getResourceType()),
                FILES_ACCESS_PATH.ACCESS_NAME.eq(pojo.getAccessName()))).limit(1).fetchOneInto(this.pojoClass);

        if (existing == null)
            return super.create(pojo);

        if (pojo.isWriteAccess() == existing.isWriteAccess()
                && pojo.isAllowSubPathAccess() == existing.isAllowSubPathAccess())
            return existing;

        if (pojo.isWriteAccess())
            existing.setWriteAccess(true);
        if (pojo.isAllowSubPathAccess())
            existing.setAllowSubPathAccess(true);

        return super.update(existing);
    }

    public boolean hasPathReadAccess(String path, ULong userId, String clientCode,
            FilesAccessPathResourceType resourceType, List<String> accessList) {

        SelectLimitPercentStep<Record1<Integer>> query = this.dslContext.select(DSL.count())
                .from(FILES_ACCESS_PATH)
                .where(DSL.and(

                        FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode), FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType),
                        DSL.or(FILES_ACCESS_PATH.USER_ID.eq(userId), FILES_ACCESS_PATH.ACCESS_NAME.in(accessList)),
                        DSL.concat(path)
                                .like(DSL.if_(FILES_ACCESS_PATH.ALLOW_SUB_PATH_ACCESS.ne(ByteUtil.ZERO),
                                        DSL.concat(FILES_ACCESS_PATH.PATH, "%"), FILES_ACCESS_PATH.PATH))))

                .limit(1);

        if (logger.isDebugEnabled())
            logger.debug(query.toString());

        int count = CommonsUtil.colease(query.fetchOneInto(Integer.class), 0);

        boolean access = count != 0;

        if (access || resourceType == FilesAccessPathResourceType.SECURED)
            return access;

        Condition condition = DSL.and(FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode),
                FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType));

        count = CommonsUtil.colease(this.dslContext.selectCount()
                .from(FILES_ACCESS_PATH)
                .where(condition)
                .limit(1)
                .fetchOneInto(Integer.class), 0);

        return count == 0;
    }

    public boolean hasPathWriteAccess(String path, ULong userId, String clientCode,
            FilesAccessPathResourceType resourceType, List<String> accessList) {

        SelectConditionStep<FilesAccessPathRecord> query = this.dslContext.selectFrom(FILES_ACCESS_PATH)
                .where(DSL.and(

                        FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode), FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType),
                        DSL.or(FILES_ACCESS_PATH.USER_ID.eq(userId), FILES_ACCESS_PATH.ACCESS_NAME.in(accessList)),

                        DSL.concat(path)
                                .like(DSL.if_(FILES_ACCESS_PATH.ALLOW_SUB_PATH_ACCESS.ne(ByteUtil.ZERO),
                                        DSL.concat(FILES_ACCESS_PATH.PATH, "%"), FILES_ACCESS_PATH.PATH))));
        if (logger.isDebugEnabled())
            logger.debug(query.toString());

        Optional<Boolean> opBoolean = query.fetchStream()
                .map(r -> r.into(FilesAccessPathRecord.class))
                .sorted((a, b) -> b.getPath().compareTo(a.getPath()))
                .filter(e -> e.getWriteAccess()
                        .equals(ByteUtil.ONE))
                .map(e -> true)
                .findFirst();

        if (opBoolean.isEmpty())
            return false;
        boolean access = opBoolean.get();

        if (access || resourceType == FilesAccessPathResourceType.SECURED)
            return access;

        return CommonsUtil.colease(this.dslContext.selectCount()
                .from(FILES_ACCESS_PATH)
                .where(DSL.and(FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode),
                        FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType)))
                .fetchOneInto(Integer.class), 0) == 0;
    }
}
