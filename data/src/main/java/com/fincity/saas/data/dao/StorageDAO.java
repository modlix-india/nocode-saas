package com.fincity.saas.data.dao;

import static com.fincity.saas.data.jooq.tables.DataStorage.DATA_STORAGE;
import static com.fincity.saas.data.jooq.tables.DataStorageField.DATA_STORAGE_FIELD;

import java.util.ArrayList;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.data.dto.Storage;
import com.fincity.saas.data.dto.StorageField;
import com.fincity.saas.data.jooq.enums.DataStorageFieldType;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;
import com.fincity.saas.data.util.DBNameUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StorageDAO extends AbstractUpdatableDAO<DataStorageRecord, ULong, Storage> {

	protected StorageDAO() {
		super(Storage.class, DATA_STORAGE, DATA_STORAGE.ID);
	}

	@Override
	public Mono<Storage> create(Storage pojo) {

		final String storageName = pojo.getName()
		        .charAt(0) == '_' ? "T" + pojo.getName() : pojo.getName();

		pojo.setInternalName(DBNameUtil.uniqueName(32, pojo.getAppCode(), pojo.getNamespace(), storageName));

		return FlatMapUtil.flatMapMono(

		        () -> super.create(pojo),

		        storage ->
				{

			        List<StorageField> fields = new ArrayList<>();
			        fields.add(new StorageField().setName("_id")
			                .setType(DataStorageFieldType.UUID)
			                .setInternalName("_id"));

			        if (pojo.getFields() != null && !pojo.getFields()
			                .isEmpty())
				        fields.addAll(pojo.getFields());

			        return Flux.fromIterable(fields)
			                .map(e -> e.setInternalName(DBNameUtil.uniqueName(32, storageName, e.getName())))
			                .map(e -> e.setStorageId(storage.getId()))
			                .map(e -> this.dslContext.newRecord(DATA_STORAGE_FIELD, e))
			                .collectList()
			                .flatMap(e -> Mono.from(this.dslContext.batchInsert(e)))
			                .map(e -> storage);
		        }

		);
	}
}
