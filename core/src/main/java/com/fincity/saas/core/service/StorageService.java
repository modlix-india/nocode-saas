package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataServcie;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.repository.StorageRepository;

import reactor.core.publisher.Mono;

@Service
public class StorageService extends AbstractOverridableDataServcie<Storage, StorageRepository> {

	protected StorageService() {
		super(Storage.class);
	}

	@Override
	protected Mono<Storage> updatableEntity(Storage entity) {
		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setSchema(entity.getSchema())
			                .setIsAudited(entity.getIsAudited())
			                .setIsVersioned(entity.getIsVersioned())
			                .setCreateAuth(entity.getCreateAuth())
			                .setReadAuth(entity.getReadAuth())
			                .setUpdateAuth(entity.getUpdateAuth())
			                .setDeleteAuth(entity.getDeleteAuth());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
