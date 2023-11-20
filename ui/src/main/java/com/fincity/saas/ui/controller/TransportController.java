package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.mongo.controller.AbstractTransportController;

@RestController
@RequestMapping("api/ui/transports")
public class TransportController extends AbstractTransportController {
    protected TransportController(ObjectMapper objectMapper) {
        super(objectMapper);
    }
}
