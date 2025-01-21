package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.TableRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.jooq.tables.records.SecurityClientHierarchyRecord;

import reactor.core.publisher.Mono;

@Component
public class ClientHierarchyDAO extends AbstractDAO<SecurityClientHierarchyRecord, ULong, ClientHierarchy> {

	protected ClientHierarchyDAO() {
		super(ClientHierarchy.class, SECURITY_CLIENT_HIERARCHY, SECURITY_CLIENT_HIERARCHY.ID);
	}

	public Mono<ClientHierarchy> getClientHierarchy(ULong clientId) {
		return Mono.from(
				this.dslContext.selectFrom(SECURITY_CLIENT_HIERARCHY)
						.where(
								SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(clientId)))
				.map(e -> e.into(ClientHierarchy.class));
	}

	public Mono<ClientHierarchy> getUserClientHierarchy(ULong userId) {
		return Mono.from(
				this.dslContext.select(SECURITY_CLIENT_HIERARCHY.fields())
						.from(SECURITY_USER)
						.join(SECURITY_CLIENT_HIERARCHY)
						.on(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.CLIENT_ID))
						.where(
								SECURITY_USER.ID.eq(userId)))
				.map(e -> e.into(ClientHierarchy.class));
	}

	public static Condition getManageClientCondition(ULong clientId) {
		return SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(clientId)
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_0.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_1.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_2.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_3.eq(clientId));
	}

	public static <T extends TableRecord<T>> Condition getManageClientCondition(TableField<T, ULong> clientId) {
		return SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(clientId)
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_0.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_1.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_2.eq(clientId))
				.or(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_3.eq(clientId));
	}
}
