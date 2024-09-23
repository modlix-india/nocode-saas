package com.fincity.security.service.appregistration;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

@Service
public class AppRegistrationIntegrationTokenService extends
        AbstractJOOQDataService<SecurityAppRegIntegrationTokensRecord, ULong, AppRegistrationIntegrationToken, AppRegistrationIntegrationTokenDao> {

}
