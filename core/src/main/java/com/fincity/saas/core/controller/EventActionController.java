package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.repository.EventActionRepository;
import com.fincity.saas.commons.core.service.EventActionService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/eventActions")
public class EventActionController
        extends AbstractOverridableDataController<EventAction, EventActionRepository, EventActionService> {

}
