package com.fincity.saas.ui.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.Application;

@Repository
public interface ApplicationRepository extends IOverridableDataRepository<Application> {

}
