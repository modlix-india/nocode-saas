package com.fincity.saas.data.dao;

import static com.fincity.saas.data.jooq.tables.DataStorage.DATA_STORAGE;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.data.dto.Storage;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;

@Service
public class StorageDAO extends AbstractUpdatableDAO<DataStorageRecord, ULong, Storage>{

	protected StorageDAO() {
		super(Storage.class, DATA_STORAGE, DATA_STORAGE.ID);
	}
}
