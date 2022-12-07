package com.fincity.saas.commons.mongo.service;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;

public abstract class AbstractAppbasedOverridableDataService<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>>
        extends AbstractOverridableDataServcie<D, R> {

	protected AbstractAppbasedOverridableDataService(Class<D> pojoClass) {
		super(pojoClass);
	}

}
