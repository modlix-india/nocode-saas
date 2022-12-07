package com.fincity.saas.data.dao;

import static com.fincity.saas.data.jooq.tables.DataStorage.DATA_STORAGE;

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

		pojo.setInternalName(DBNameUtil.uniqueName(32, pojo.getAppCode(), pojo.getNamespace(), pojo.getName()));

		return FlatMapUtil.flatMapMono(

		        () -> super.create(pojo),

		        storage ->
				{

			        List<StorageField> fields = new ArrayList<>();
			        fields.add(new StorageField().setName("_id")
			                .setType(DataStorageFieldType.UUID));

			        if (pojo.getFields() != null && !pojo.getFields()
			                .isEmpty())
				        fields.addAll(pojo.getFields());

			        return Flux.fromIterable(fields)
			                .flatMap(e -> this.createField(storage.getId(), e))
			                .collectList()
			                .map(e -> storage);
		        }

		);
	}

	private Mono<StorageField> createField(ULong storageId, StorageField field) {

		return null;
	}
}
