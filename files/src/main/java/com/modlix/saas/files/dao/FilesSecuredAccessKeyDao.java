package com.modlix.saas.files.dao;

import static com.modlix.saas.files.jooq.tables.FilesSecuredAccessKeys.FILES_SECURED_ACCESS_KEYS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.jooq.dao.AbstractDAO;
import com.modlix.saas.files.dto.FilesSecuredAccessKey;
import com.modlix.saas.files.jooq.tables.records.FilesSecuredAccessKeysRecord;

@Service
public class FilesSecuredAccessKeyDao extends AbstractDAO<FilesSecuredAccessKeysRecord, ULong, FilesSecuredAccessKey> {

    protected FilesSecuredAccessKeyDao() {
        super(FilesSecuredAccessKey.class, FILES_SECURED_ACCESS_KEYS, FILES_SECURED_ACCESS_KEYS.ID);
    }

    public FilesSecuredAccessKey getAccessByKey(String key) {

        return this.dslContext.selectFrom(FILES_SECURED_ACCESS_KEYS)
                .where(FILES_SECURED_ACCESS_KEYS.ACCESS_KEY.eq(key))
                .limit(1)
                .fetchOneInto(FilesSecuredAccessKey.class);

    }

    public boolean incrementAccessCount(ULong id) {

        return this.dslContext.update(FILES_SECURED_ACCESS_KEYS)
                .set(FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT, FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT.add(1))
                .where(FILES_SECURED_ACCESS_KEYS.ID.eq(id)
                        .and(FILES_SECURED_ACCESS_KEYS.ACCESSED_COUNT
                                .le(FILES_SECURED_ACCESS_KEYS.ACCESS_LIMIT)))
                .execute() > 0;
    }
}
