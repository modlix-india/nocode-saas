package com.fincity.saas.ui.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.fincity.saas.ui.document.Version;

@Repository
public interface VersionRepository  extends ReactiveCrudRepository<Version, String>{

}
