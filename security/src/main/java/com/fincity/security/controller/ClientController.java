package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.service.ClientService;

@RestController
@RequestMapping("api/security/clients")
public class ClientController extends AbstractJOOQDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {

}
