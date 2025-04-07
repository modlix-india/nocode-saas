package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.saas.commons.core.dao.AbstractCoreTokenDao;
import com.fincity.saas.commons.service.CacheService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public abstract class AbstractRestTokenService<R extends UpdatableRecord<R>, O extends AbstractCoreTokenDao<R>>
        extends AbstractRestService {

    protected O coreTokenDao;

    protected BasicRestService basicRestService;

    protected CacheService cacheService;

    protected AbstractRestTokenService(O coreTokenDao) {
        this.coreTokenDao = coreTokenDao;
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
