package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.saas.commons.core.dao.CoreTokenDAO;
import com.fincity.saas.commons.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public abstract class AbstractRestTokenService extends AbstractRestService {

    protected CoreTokenDAO coreTokenDAO;

    protected BasicRestService basicRestService;

    protected CacheService cacheService;

    @Autowired
    private void setTokenDAO(CoreTokenDAO coreTokenDAO) {
        this.coreTokenDAO = coreTokenDAO;
    }

    @Autowired
    private void setBasicRestService(BasicRestService basicRestService) {
        this.basicRestService = basicRestService;
    }

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }
}
