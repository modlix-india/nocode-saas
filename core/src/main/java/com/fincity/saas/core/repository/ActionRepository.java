package com.fincity.saas.core.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.Action;

@Repository
public interface ActionRepository extends IOverridableDataRepository<Action> {

}
