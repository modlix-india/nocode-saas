package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.repository.ConnectionRepository;
import com.fincity.saas.core.service.ConnectionService;

@RestController
@RequestMapping("api/core/connections")
public class ConnectionController extends AbstractOverridableDataController<Connection, ConnectionRepository, ConnectionService> {

}
