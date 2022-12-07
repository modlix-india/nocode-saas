package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.document.Version;

@Repository
public interface VersionRepository  extends ReactiveCrudRepository<Version, String>{

}
