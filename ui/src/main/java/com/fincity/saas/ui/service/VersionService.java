package com.fincity.saas.ui.service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoDataService;
import com.fincity.saas.ui.document.Version;
import com.fincity.saas.ui.repository.VersionRepository;

import reactor.core.publisher.Mono;

public class VersionService extends AbstractMongoDataService<String, Version, VersionRepository> {

	protected VersionService() {
		super(Version.class);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser)
		        .map(ContextUser::getId)
		        .map(Object::toString);
	}
}
