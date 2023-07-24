package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.document.Transport;

public interface TransportRepository
        extends ReactiveCrudRepository<Transport, String>, IOverridableDataRepository<Transport> {

}
