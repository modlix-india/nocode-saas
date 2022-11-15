package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.PastPasswordDAO;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.jooq.tables.records.SecurityPastPasswordsRecord;

@Service
public class PastPasswordService
        extends AbstractJOOQDataService<SecurityPastPasswordsRecord, ULong, PastPassword, PastPasswordDAO> {

}
