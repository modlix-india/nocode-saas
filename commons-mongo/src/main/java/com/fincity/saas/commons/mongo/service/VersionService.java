package com.fincity.saas.commons.mongo.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.document.Version;
import com.fincity.saas.commons.mongo.repository.VersionRepository;

import reactor.core.publisher.Mono;

@Service
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
