package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;

import reactor.core.publisher.Mono;

@Service
public class UserService extends AbstractUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

	public Mono<User> findByClientIdsUserName(ULong clientId, String userName,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		return this.dao.getBy(clientId, userName, authenticationIdentifierType).flatMap(this.dao::setPermissions);
	}

	public Mono<User> getUserForContext(ULong id) {
		return this.dao.readById(id).flatMap(this.dao::setPermissions);
	}

	public Mono<Object> increaseFailedAttempt(ULong userId) {

		return this.dao.increaseFailedAttempt(userId);
	}

	public Mono<Object> resetFailedAttempt(ULong userId) {
		return this.dao.resetFailedAttempt(userId);
	}
}
