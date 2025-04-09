package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Workflow;
import com.fincity.saas.commons.core.repository.WorkflowRepository;
import com.fincity.saas.commons.core.service.WorkflowService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/workflows")
public class WorkflowController extends AbstractOverridableDataController<Workflow, WorkflowRepository, WorkflowService> {

}
