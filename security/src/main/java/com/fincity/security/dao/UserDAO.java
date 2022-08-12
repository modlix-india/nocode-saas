package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import org.jooq.Record1;
import org.jooq.SelectOrderByStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.User;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserDAO extends AbstractUpdatableDAO<SecurityUserRecord, ULong, User> {

	protected UserDAO() {
		super(User.class, SECURITY_USER, SECURITY_USER.ID);
	}

	public Mono<User> getBy(ULong clientId, String userName,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		TableField<SecurityUserRecord, String> field = SECURITY_USER.USER_NAME;
		if (authenticationIdentifierType == AuthenticationIdentifierType.EMAIL_ID) {
			field = SECURITY_USER.EMAIL_ID;
		} else if (authenticationIdentifierType == AuthenticationIdentifierType.PHONE_NUMBER) {
			field = SECURITY_USER.PHONE_NUMBER;
		}

		return Mono.from(this.dslContext.select(SECURITY_USER.fields())
		        .from(SECURITY_USER)
		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		        .where(field.eq(userName)
		                .and(SECURITY_USER.CLIENT_ID.eq(clientId)
		                        .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(clientId))))
		        .limit(1))
		        .map(e -> e.into(User.class));
	}

	public Mono<Object> increaseFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.NO_FAILED_ATTEMPT, SECURITY_USER.NO_FAILED_ATTEMPT.add(1))
		        .where(SECURITY_USER.ID.eq(userId)));
	}

	public Mono<Object> resetFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.NO_FAILED_ATTEMPT, Short.valueOf((short) 0))
		        .where(SECURITY_USER.ID.eq(userId)));
	}

	public Mono<User> setPermissions(User user) {

		SelectOrderByStep<Record1<String>> query = this.dslContext.select(SECURITY_PERMISSION.NAME)
		        .from(SECURITY_USER_ROLE_PERMISSION)
		        .join(SECURITY_ROLE)
		        .on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID))
		        .join(SECURITY_ROLE_PERMISSION)
		        .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_ROLE.ID))
		        .join(SECURITY_PERMISSION)
		        .on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))
		        .union(this.dslContext.select(SECURITY_PERMISSION.NAME)
		                .from(SECURITY_USER_ROLE_PERMISSION)
		                .join(SECURITY_PERMISSION)
		                .on(SECURITY_PERMISSION.ID.eq(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)))
		        .union(this.dslContext.select(DSL.concat("ROLE_" + SECURITY_ROLE.NAME))
		                .from(SECURITY_USER_ROLE_PERMISSION)
		                .join(SECURITY_ROLE)
		                .on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID)));

		return Flux.from(query)
		        .map(Record1::value1)
		        .collectList()
		        .map(user::setAuthorities);
	}
}
