package com.fincity.saas.core.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.Storage;

@Repository
public interface StorageRepository extends IOverridableDataRepository<Storage> {

}
