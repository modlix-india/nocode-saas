package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.EventDefinition;
import com.fincity.saas.core.repository.EventDefinitionRepository;
import com.fincity.saas.core.service.EventDefinitionService;

@RestController
@RequestMapping("api/core/eventDefinitions")
public class EventDefinitionController
        extends AbstractOverridableDataController<EventDefinition, EventDefinitionRepository, EventDefinitionService> {

}
