package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.EventAction;
import com.fincity.saas.core.repository.EventActionRepository;
import com.fincity.saas.core.service.EventActionService;

@RestController
@RequestMapping("api/core/eventActions")
public class EventActionController
        extends AbstractOverridableDataController<EventAction, EventActionRepository, EventActionService> {

}
