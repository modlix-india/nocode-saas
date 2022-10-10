package com.fincity.saas.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.repository.IUIRepository;

import reactor.core.publisher.Mono;

public abstract class AbstractAppbasedUIService<D extends AbstractUIDTO<D>, R extends IUIRepository<D>>
        extends AbstractUIServcie<D, R> {

	@Autowired
	private ApplicationService appService;

	protected AbstractAppbasedUIService(Class<D> pojoClass) {
		super(pojoClass);
	}

	@Override
	protected Mono<List<String>> inheritanceOrder(String appName, String clientCode) {
		return this.appService.inheritanceOrder(appName, clientCode);
	}
}
