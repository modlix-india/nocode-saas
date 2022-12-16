package com.fincity.saas.ui.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.Page;

@Repository
public interface PageRepository extends IOverridableDataRepository<Page> {

}
