package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesAccessPath.FILES_ACCESS_PATH;

import java.util.List;

import org.jooq.Record1;
import org.jooq.SelectLimitPercentStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.files.dto.FilesAccessPath;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;


import reactor.core.publisher.Mono;

@Service
public class FilesAccessPathDao extends AbstractUpdatableDAO<FilesAccessPathRecord, ULong, FilesAccessPath> {

	private static final Logger logger = LoggerFactory.getLogger(FilesAccessPathDao.class);

	protected FilesAccessPathDao() {
		super(FilesAccessPath.class, FILES_ACCESS_PATH, FILES_ACCESS_PATH.ID);
	}

	public Mono<Boolean> hasPathReadAccess(String path, ULong userId, String clientCode,
	        FilesAccessPathResourceType resourceType, List<String> accessList) {
		
		SelectLimitPercentStep<Record1<Integer>> query = this.dslContext.select(DSL.count())
		        .from(FILES_ACCESS_PATH)
		        .where(DSL.and(

		                FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode), FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType),
		                DSL.or(FILES_ACCESS_PATH.USER_ID.eq(userId), FILES_ACCESS_PATH.ACCESS_NAME.in(accessList)),
		                DSL.concat(path)
		                        .like(DSL.if_(FILES_ACCESS_PATH.ALLOW_SUB_PATH_ACCESS.ne(Byte.valueOf((byte) 0)),
		                                DSL.concat(FILES_ACCESS_PATH.PATH, "%"), FILES_ACCESS_PATH.PATH))))

		        .limit(1);
		
		if (logger.isDebugEnabled())
			logger.debug(query.toString());
		return Mono.from(query)
		        .map(Record1::value1)
		        .map(e -> e != 0);
	}

	public Mono<Boolean> hasPathWriteAccess(String path, ULong userId, String clientCode,
	        FilesAccessPathResourceType resourceType, List<String> accessList) {

		SelectLimitPercentStep<Record1<Integer>> query = this.dslContext.select(DSL.count())
		        .from(FILES_ACCESS_PATH)
		        .where(DSL.and(

		                FILES_ACCESS_PATH.CLIENT_CODE.eq(clientCode), FILES_ACCESS_PATH.RESOURCE_TYPE.eq(resourceType),
		                DSL.or(FILES_ACCESS_PATH.USER_ID.eq(userId), FILES_ACCESS_PATH.ACCESS_NAME.in(accessList)),
		                FILES_ACCESS_PATH.WRITE_ACCESS.ne(Byte.valueOf((byte) 0)),

		                DSL.concat(path)
		                        .like(DSL.if_(FILES_ACCESS_PATH.ALLOW_SUB_PATH_ACCESS.ne(Byte.valueOf((byte) 0)),
		                                DSL.concat(FILES_ACCESS_PATH.PATH, "%"), FILES_ACCESS_PATH.PATH))))

		        .limit(1);
		if (logger.isDebugEnabled())
			logger.debug(query.toString());
		return Mono.from(query)
		        .map(Record1::value1)
		        .map(e -> e != 0);
	}
}
