package com.fincity.security.service;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.security.dto.LimitAccess;

public abstract class AbstractSecurityUpdatableLimitDataService<R extends UpdatableRecord<R>, O extends AbstractUpdatableDAO<R, ULong, LimitAccess>>
        extends AbstractJOOQUpdatableDataService<R, ULong, LimitAccess, O> {

	public abstract UpdatableRecord<R> getLimitRecord();

	public abstract AbstractUpdatableDAO<R, ULong, LimitAccess> getUpdatableDao();
}
