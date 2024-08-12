package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.mongo.controller.AbstractTransportController;
import com.fincity.saas.core.service.CoreMessageResourceService;

@RestController
@RequestMapping("api/core/transports")
public class TransportController extends AbstractTransportController {

    public TransportController(ObjectMapper objectMapper, CoreMessageResourceService messageService) {
        super(objectMapper, messageService);
    }

}
