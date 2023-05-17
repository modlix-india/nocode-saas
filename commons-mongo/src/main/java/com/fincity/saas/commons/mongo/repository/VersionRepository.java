package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.document.Version;

public interface VersionRepository  extends ReactiveCrudRepository<Version, String>{

}
