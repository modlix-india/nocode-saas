package com.fincity.saas.core.service.connection.rest;

import com.fincity.saas.commons.core.service.connection.rest.OAuth2RestService;
import com.fincity.saas.core.dao.CoreTokenDao;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;
import org.springframework.stereotype.Service;

@Service
public class CoreOAuth2RestService extends OAuth2RestService<CoreTokensRecord, CoreTokenDao> {

    protected CoreOAuth2RestService(CoreTokenDao coreTokenDao) {
        super(coreTokenDao);
    }
}
