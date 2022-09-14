package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;

import java.util.HashSet;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class ClientDAO extends AbstractUpdatableDAO<SecurityClientRecord, ULong, Client> {

	protected ClientDAO() {
		super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
	}

	public Mono<Set<ULong>> findManagedClientList(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.ID)
		        .from(SECURITY_CLIENT)
		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(SECURITY_CLIENT.ID.eq(id)
		                .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(id))))
		        .map(Record1::value1)
		        .collectList()
		        .map(HashSet::new);
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong clientId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId))
		        .limit(1))
		        .map(e -> e.into(ClientPasswordPolicy.class));
	}

	public Mono<String> getClientType(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.TYPE_CODE)
		        .from(SECURITY_CLIENT)
		        .where(SECURITY_CLIENT.ID.eq(id))
		        .limit(1))
		        .take(1)
		        .singleOrEmpty()
		        .map(Record1::value1);
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {

		if (managingClientId.equals(clientId))
			return Mono.just(true);

		return Mono.from(this.dslContext.select(DSL.count())
		        .from(SECURITY_CLIENT_MANAGE)
		        .where(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(clientId)
		                .and(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(managingClientId)))
		        .limit(1))
		        .map(Record1::value1)
		        .map(e -> e == 1);
	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> x = super.getSelectJointStep();

			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        return x;

			        return x.map(tup -> tup
			                .mapT1(query -> (SelectJoinStep<Record>) query.leftJoin(SECURITY_CLIENT_MANAGE)
			                        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_CLIENT.ID)))
			                .mapT2(query -> query.leftJoin(SECURITY_CLIENT_MANAGE)
			                        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_CLIENT.ID))));

		        });
	}

	@Override
	protected Mono<Condition> filter(AbstractCondition condition) {

		return super.filter(condition).flatMap(cond -> SecurityContextUtil.getUsersContextAuthentication()
		        .map(ca ->
				{

			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        return cond;

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());
			        return DSL.and(cond, SECURITY_CLIENT.ID.eq(clientId)
			                .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(clientId)));
		        }));
	}

	public Mono<Integer> addManageRecord(ULong manageClientId, ULong id) {
		return Mono.from(this.dslContext
		        .insertInto(SECURITY_CLIENT_MANAGE, SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID,
		                SECURITY_CLIENT_MANAGE.CLIENT_ID)
		        .values(manageClientId, id));
	}

	public Mono<Client> readInternal(ULong id) {
		return Mono.from(this.dslContext.selectFrom(this.table)
		        .where(this.idField.eq(id))
		        .limit(1))
		        .map(e -> e.into(this.pojoClass));
	}

	public int addPackageToClient(ULong clientId, ULong packageId) {

		return this.dslContext
		        .insertInto(SECURITY_CLIENT_PACKAGE, SECURITY_CLIENT_PACKAGE.CLIENT_ID,
		                SECURITY_CLIENT_PACKAGE.PACKAGE_ID)
		        .values(clientId, packageId)
		        .execute();

	}

	public Mono<Boolean> checkClientApplicableForGivenPackage(ULong clientId, ULong packageId) {
		return Mono.just(this.dslContext.select(DSL.count())
		        .from(SECURITY_PACKAGE)
		        .where(SECURITY_PACKAGE.ID.eq(packageId)
		                .and(SECURITY_PACKAGE.CLIENT_ID.eq(clientId)))
		        .execute() > 0);
	}

	public Mono<Boolean> checkPackageApplicableForGivenClient(ULong clientId, ULong packageId) {
		return Mono.just(this.dslContext.select(DSL.count())
		        .from(SECURITY_CLIENT_PACKAGE)
		        .where(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)
		                .and(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId)))
		        .execute() > 0);
	}

	public Mono<Boolean> checkPermissionAvailableForGivenClient(ULong clientId, ULong permissionId) {

		Condition clientCondition = SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)

		        .or(SECURITY_PACKAGE.CLIENT_ID.eq(clientId))
		        .or(SECURITY_PACKAGE.BASE.eq((byte) 1));

		Condition permissionCondition = SECURITY_PERMISSION.ID.eq(permissionId);

		return Mono.just(

		        this.dslContext.select()
		                .from(SECURITY_PACKAGE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_PACKAGE.ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .leftJoin(SECURITY_ROLE_PERMISSION)
		                .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
		                .leftJoin(SECURITY_PERMISSION)
		                .on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))
		                .where(clientCondition.and(permissionCondition))
		                .execute() > 0

		);
	}

}
