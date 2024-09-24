package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesSecuredAccessKeys.FILES_SECURED_ACCESS_KEYS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.tables.records.FilesSecuredAccessKeysRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesSecuredAccessKeyDao extends AbstractDAO<FilesSecuredAccessKeysRecord, ULong, FilesSecuredAccessKey> {

	protected FilesSecuredAccessKeyDao() {
		super(FilesSecuredAccessKey.class, FILES_SECURED_ACCESS_KEYS, FILES_SECURED_ACCESS_KEYS.ID);
	}

	public Mono<FilesSecuredAccessKey> getAccessByKey(String key) {

		return Mono.from(

				this.dslContext.selectFrom(FILES_SECURED_ACCESS_KEYS)
						.where(FILES_SECURED_ACCESS_KEYS.ACCESS_KEY.eq(key))
						.limit(1))
				.map(e -> e.into(FilesSecuredAccessKey.class));

	}

	public Mono<Boolean> incrementAccessCount(ULong id) {

		return Mono.from(

				this.dslContext.update(FILES_SECURED_ACCESS_KEYS)
						.set(FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT, FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT.add(1))
						.where(FILES_SECURED_ACCESS_KEYS.ID.eq(id)
								.and(FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT
										.le(FILES_SECURED_ACCESS_KEYS.ACCESS_LIMIT))))
				.map(result -> result > 0);
	}
}
