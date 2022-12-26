package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesSecuredAccessKey.FILES_SECURED_ACCESS_KEY;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.tables.records.FilesSecuredAccessKeyRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesSecuredAccessKeyDao extends AbstractDAO<FilesSecuredAccessKeyRecord, ULong, FilesSecuredAccessKey> {

	protected FilesSecuredAccessKeyDao() {
		super(FilesSecuredAccessKey.class, FILES_SECURED_ACCESS_KEY, FILES_SECURED_ACCESS_KEY.ID);
	}

	public Mono<FilesSecuredAccessKey> getAccessByKey(String key) {

		return Mono.from(

		        this.dslContext.selectFrom(FILES_SECURED_ACCESS_KEY)
		                .where(FILES_SECURED_ACCESS_KEY.ACCESS_KEY.eq(key))
		                .limit(1))
		        .map(e -> e.into(FilesSecuredAccessKey.class));

	}

	public Mono<Boolean> setAccessCount(ULong id, ULong count) {

		return Mono.from(

		        this.dslContext.update(FILES_SECURED_ACCESS_KEY)
		                .set(FILES_SECURED_ACCESS_KEY.ACCESSED_COUNT, count)
		                .where(FILES_SECURED_ACCESS_KEY.ID.eq(id)))
		        .map(result -> result > 0);
	}
}
