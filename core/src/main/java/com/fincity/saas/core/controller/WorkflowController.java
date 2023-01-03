package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Workflow;
import com.fincity.saas.core.repository.WorkflowRepository;
import com.fincity.saas.core.service.WorkflowService;

@RestController
@RequestMapping("api/core/workflows")
public class WorkflowController extends AbstractOverridableDataController<Workflow, WorkflowRepository, WorkflowService> {

}
