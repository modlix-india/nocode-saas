package com.fincity.saas.core.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.Connection;

@Repository
public interface ConnectionRepository extends IOverridableDataRepository<Connection> {

}
