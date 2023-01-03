package com.fincity.saas.ui.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.UIFunction;

@Repository
public interface UIFunctionDocumentRepository extends IOverridableDataRepository<UIFunction> {

}
