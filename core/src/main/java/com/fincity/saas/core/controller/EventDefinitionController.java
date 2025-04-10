package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.repository.EventDefinitionRepository;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/eventDefinitions")
public class EventDefinitionController
        extends AbstractOverridableDataController<EventDefinition, EventDefinitionRepository, EventDefinitionService> {

}
