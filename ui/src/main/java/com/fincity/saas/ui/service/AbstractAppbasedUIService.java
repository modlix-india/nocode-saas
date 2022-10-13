package com.fincity.saas.ui.service;

import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.repository.IUIRepository;

public abstract class AbstractAppbasedUIService<D extends AbstractUIDTO<D>, R extends IUIRepository<D>>
        extends AbstractUIServcie<D, R> {

	protected AbstractAppbasedUIService(Class<D> pojoClass) {
		super(pojoClass);
	}

}
