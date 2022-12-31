package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Action;
import com.fincity.saas.core.repository.ActionRepository;
import com.fincity.saas.core.service.ActionService;

@RestController
@RequestMapping("api/core/workflow/actions")
public class ActionController extends AbstractOverridableDataController<Action, ActionRepository, ActionService> {

}
