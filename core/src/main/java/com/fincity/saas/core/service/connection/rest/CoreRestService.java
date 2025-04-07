package com.fincity.saas.core.service.connection.rest;

import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.connection.rest.BasicRestService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.fincity.saas.core.dao.CoreTokenDao;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;
import org.springframework.stereotype.Service;

@Service
public class CoreRestService extends RestService<CoreTokensRecord, CoreTokenDao> {

    protected CoreRestService(
            ConnectionService connectionService,
            BasicRestService basicRestService,
            CoreOAuth2RestService oAuth2RestService,
            CoreRestAuthService restAuthService) {
        super(connectionService, basicRestService, oAuth2RestService, restAuthService);
    }
}
