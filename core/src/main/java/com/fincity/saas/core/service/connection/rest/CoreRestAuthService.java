package com.fincity.saas.core.service.connection.rest;

import com.fincity.saas.commons.core.service.connection.rest.RestAuthService;
import com.fincity.saas.core.dao.CoreTokenDao;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;
import org.springframework.stereotype.Service;

@Service
public class CoreRestAuthService extends RestAuthService<CoreTokensRecord, CoreTokenDao> {

  protected CoreRestAuthService(CoreTokenDao coreTokenDao) {
    super(coreTokenDao);
  }
}
