package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractTransportController;

@RestController
@RequestMapping("api/core/transports")
public class TransportController extends AbstractTransportController {

}
