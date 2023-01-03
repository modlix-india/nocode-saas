package com.fincity.saas.core.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.core.document.Workflow;

@Repository
public interface WorkflowRepository extends IOverridableDataRepository<Workflow> {

}
