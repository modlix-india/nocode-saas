package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesAccessPath.*;

import java.util.List;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.files.dto.FilesAccessPath;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class FilesAccessPathDao extends AbstractUpdatableDAO<FilesAccessPathRecord, ULong, FilesAccessPath> {

    protected FilesAccessPathDao() {
        super(FilesAccessPath.class, FILES_ACCESS_PATH, FILES_ACCESS_PATH.ID);
    }

    public Mono<FilesAccessPath> create(FilesAccessPath pojo) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> Mono.from(dslContext.selectFrom(this.table).where(DSL.and(
                        FILES_ACCESS_PATH.PATH.eq(pojo.getPath()),
                        FILES_ACCESS_PATH.CLIENT_CODE.eq(pojo.getClientCode()),
                        FILES_ACCESS_PATH.RESOURCE_TYPE.eq(pojo.getResourceType()),
                        FILES_ACCESS_PATH.ACCESS_NAME.eq(pojo.getAccessName()))).limit(1)).map(e -> e.into(this.pojoClass)),

                existing -> {
                    if (existing == null) return super.create(pojo);

                    if (pojo.isWriteAccess() == existing.isWriteAccess() && pojo.isAllowSubPathAccess() == existing.isAllowSubPathAccess())
                        return Mono.just(existing);

                    if (pojo.isWriteAccess()) existing.setWriteAccess(true);
                    if (pojo.isAllowSubPathAccess()) existing.setAllowSubPathAccess(true);

                    return super.update(existing);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "FilesAccessPathDao.create"));
    }

    public Mono<Boolean> hasPathReadAccess(String path, ULong userId, String clientCode,
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
        return Mono.from(query)
                .map(Record1::value1)
                .map(e -> e != 0)
                .flatMap(access -> {

                    if (BooleanUtil.safeValueOf(access) || resourceType == FilesAccessPathResourceType.SECURED)
                        return Mono.just(access);

                    return Mono.from(this.dslContext.selectCount()
                                    .from(FILES_ACCESS_PATH)
                                    .where(DSL.and(FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode),
                                            FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType))))
                            .map(Record1::value1)
                            .map(e -> e == 0);
                });
    }

    public Mono<Boolean> hasPathWriteAccess(String path, ULong userId, String clientCode,
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

        return Flux.from(query)
                .sort((a, b) -> b.getPath()
                        .compareTo(a.getPath()))
                .next()
                .filter(e -> e.getWriteAccess()
                        .equals(ByteUtil.ONE))
                .map(e -> true)
                .defaultIfEmpty(false)
                .flatMap(access -> {

                    if (BooleanUtil.safeValueOf(access) || resourceType == FilesAccessPathResourceType.SECURED)
                        return Mono.just(access);

                    return Mono.from(this.dslContext.selectCount()
                                    .from(FILES_ACCESS_PATH)
                                    .where(DSL.and(FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode),
                                            FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType))))
                            .map(Record1::value1)
                            .map(e -> e == 0);
                });
    }
}
