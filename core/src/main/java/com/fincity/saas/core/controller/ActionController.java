package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Action;
import com.fincity.saas.commons.core.repository.ActionRepository;
import com.fincity.saas.commons.core.service.ActionService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/workflow/actions")
public class ActionController extends AbstractOverridableDataController<Action, ActionRepository, ActionService> {

}
