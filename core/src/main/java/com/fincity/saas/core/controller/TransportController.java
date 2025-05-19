package com.fincity.saas.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.mongo.controller.AbstractTransportController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/transports")
public class TransportController extends AbstractTransportController {

    public TransportController(ObjectMapper objectMapper, CoreMessageResourceService messageService) {
        super(objectMapper, messageService);
    }
}
