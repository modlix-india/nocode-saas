package com.fincity.saas.core.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.CoreSchema;

@Repository
public interface CoreSchemaDocumentRepository extends IOverridableDataRepository<CoreSchema> {

}
