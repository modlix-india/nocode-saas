package com.fincity.saas.data.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.data.dao.ConnectionDAO;
import com.fincity.saas.data.dto.Connection;
import com.fincity.saas.data.jooq.tables.records.DataConnectionRecord;
import com.fincity.saas.data.service.ConnectionService;

@RestController
@RequestMapping("/api/data/connection")
public class ConnectionController extends AbstractJOOQUpdatableDataController<DataConnectionRecord, ULong, Connection, ConnectionDAO, ConnectionService>{

}
