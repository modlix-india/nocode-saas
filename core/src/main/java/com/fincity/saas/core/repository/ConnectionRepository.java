package com.fincity.saas.core.repository;

import java.util.List;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.enums.ConnectionType;

import reactor.core.publisher.Mono;


public interface ConnectionRepository extends IOverridableDataRepository<Connection> {

	Mono<List<Connection>> findByConnectionTypeAndAppCodeAndClientCodeIn(ConnectionType connectionType, String appCode,
	                                                                     List<String> clientCodes);

}
