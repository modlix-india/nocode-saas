package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.mongo.controller.AbstractTransportController;
import com.fincity.saas.ui.service.UIMessageResourceService;

@RestController
@RequestMapping("api/ui/transports")
public class TransportController extends AbstractTransportController {

    public TransportController(ObjectMapper objectMapper, UIMessageResourceService messageService) {
        super(objectMapper, messageService);
    }
}
