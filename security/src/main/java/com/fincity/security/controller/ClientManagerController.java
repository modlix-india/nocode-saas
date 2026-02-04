package com.fincity.security.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;
import com.fincity.security.service.ClientManagerService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/security/client-managers")
public class ClientManagerController
        extends AbstractJOOQDataController<SecurityClientManagerRecord, ULong, ClientManager, ClientManagerDAO, ClientManagerService> {
}
