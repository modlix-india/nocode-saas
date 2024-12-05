package com.fincity.security.dao;

import static com.fincity.saas.commons.util.StringUtil.removeSpecialCharacters;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Package;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.tables.records.SecurityClientPackageRecord;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

	public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.TYPE_CODE, SECURITY_CLIENT.CODE)
				.from(SECURITY_CLIENT)
				.where(SECURITY_CLIENT.ID.eq(id))
				.limit(1))
				.take(1)
				.singleOrEmpty()
				.map(r -> Tuples.of(r.value1(), r.value2()));
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {

		if (managingClientId.equals(clientId))
			return Mono.just(true);

		return FlatMapUtil.flatMapMonoWithNull(

				() -> Mono.from(this.dslContext.select(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID)
						.from(SECURITY_CLIENT_MANAGE).where(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(clientId)))
						.map(Record1::value1),

				manageClientId -> {
					if (manageClientId != null)
						return Mono.just(manageClientId.equals(managingClientId));

					return Mono.from(Mono.from(this.dslContext.select(DSL.count())
							.from(SECURITY_CLIENT)
							.where(SECURITY_CLIENT.ID.eq(managingClientId)
									.and(SECURITY_CLIENT.TYPE_CODE.eq("SYS")))
							.limit(1))
							.map(Record1::value1)
							.map(e -> e == 1));
				},

				(manageClientId, managed) -> {
					if (BooleanUtil.safeValueOf(managed))
						return Mono.just(managed);

					if (manageClientId == null)
						return Mono.just(false);

					return this.isBeingManagedBy(managingClientId, manageClientId);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientDAO.isBeingManagedBy"));
	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

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
				.map(ca -> {

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

	public Mono<Integer> addManageRecord(String clientCode, ULong id) {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
				.from(SECURITY_CLIENT)
				.where(SECURITY_CLIENT.CODE.eq(clientCode))
				.limit(1))
				.map(Record1::value1)
				.flatMap(manageClientId -> Mono.from(this.dslContext
						.insertInto(SECURITY_CLIENT_MANAGE, SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID,
								SECURITY_CLIENT_MANAGE.CLIENT_ID)
						.values(manageClientId, id)));
	}

	public Mono<Client> readInternal(ULong id) {
		return Mono.from(this.dslContext.selectFrom(this.table)
				.where(this.idField.eq(id))
				.limit(1))
				.map(e -> e.into(this.pojoClass));
	}

	public Mono<Boolean> addPackageToClient(ULong clientId, ULong packageId) {

		return Mono
				.from(this.dslContext
						.insertInto(SECURITY_CLIENT_PACKAGE, SECURITY_CLIENT_PACKAGE.CLIENT_ID,
								SECURITY_CLIENT_PACKAGE.PACKAGE_ID)
						.values(clientId, packageId))
				.map(val -> val > 0);

	}

	public Mono<Boolean> checkPermissionExistsOrCreatedForClient(ULong clientId, ULong permissionId) {

		Condition clientCondition = SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)
				.and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId));

		Condition assignedOrBasePackageCondition = clientCondition.or(SECURITY_PACKAGE.BASE.eq((byte) 1));

		Condition permissionCreatedCondition = SECURITY_PERMISSION.CLIENT_ID.eq(clientId);

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_PERMISSION)
						.leftJoin(SECURITY_ROLE_PERMISSION)
						.on(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(SECURITY_PERMISSION.ID))
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
						.leftJoin(SECURITY_PACKAGE)
						.on(SECURITY_PACKAGE.ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
						.where(assignedOrBasePackageCondition.or(permissionCreatedCondition)))
				.map(Record1::value1)
				.map(count -> count > 0);
	}

	public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {

		if (managingClientCode.equals(clientCode))
			return Mono.just(true);

		return FlatMapUtil.flatMapMono(

				() -> this.getClientBy(clientCode),

				c -> this.getClientBy(managingClientCode),

				(c, m) -> this.getManagingClientId(c.getId())
						.switchIfEmpty(Mono.defer(this::getSystemClientId)),

				(c, m, mcid) -> Mono.just(m.getId().equals(mcid)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientDAO.isBeingManagedBy"));
	}

	public Mono<Boolean> isUserBeingManaged(ULong userId, String clientCode) {

		Table<SecurityClientRecord> orig = SECURITY_CLIENT.asTable("orig");
		Table<SecurityClientRecord> manage = SECURITY_CLIENT.asTable("manage");

		return Mono.from(this.dslContext.select(DSL.count())
				.from(SECURITY_USER)
				.leftJoin(SECURITY_CLIENT_MANAGE)
				.on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
				.leftJoin(manage)
				.on(manage.field("ID", ULong.class)
						.eq(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID))
				.leftJoin(orig)
				.on(orig.field("ID", ULong.class)
						.eq(SECURITY_USER.CLIENT_ID))
				.where(DSL.or(SECURITY_USER.ID.eq(userId)
						.and(manage.field("CODE", String.class)
								.eq(clientCode)),
						orig.field("CODE", String.class)
								.eq(clientCode)))
				.limit(1))
				.map(Record1::value1)
				.map(e -> e == 1);
	}

	public Mono<Boolean> makeClientActiveIfInActive(ULong clientId) {

		return Mono.from(this.dslContext.update(SECURITY_CLIENT)
		        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.ACTIVE)
		        .where(SECURITY_CLIENT.ID.eq(clientId)
		                .and(SECURITY_CLIENT.STATUS_CODE.eq(SecurityClientStatusCode.INACTIVE))))
		        .map(e -> e > 0);

	}

	public Mono<Boolean> makeClientInActive(ULong clientId) {

		return Mono.from(this.dslContext.update(SECURITY_CLIENT)
		        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.INACTIVE)
		        .where(SECURITY_CLIENT.ID.eq(clientId)
		                .and(SECURITY_CLIENT.STATUS_CODE.ne(SecurityClientStatusCode.DELETED))))
		        .map(e -> e > 0);
	}

	public Mono<Client> getClientBy(String clientCode) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.fields())
				.from(SECURITY_CLIENT)
				.where(SECURITY_CLIENT.CODE.eq(clientCode))
				.limit(1))
				.singleOrEmpty()
				.map(e -> e.into(Client.class));
	}

	public Mono<List<Client>> getClientsBy(List<ULong> clientIds) {

		return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT)
				.where(SECURITY_CLIENT.ID.in(clientIds))).map(e -> e.into(Client.class)).collectList();
	}

	public Mono<Boolean> checkRoleExistsOrCreatedForClient(ULong clientId, ULong roleId) {

		Condition packageCondition = SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)
				.or(SECURITY_PACKAGE.BASE.eq((byte) 1));

		Condition roleCondition = SECURITY_ROLE.ID.eq(roleId);

		Condition roleExistsCondition = packageCondition.and(roleCondition);

		Condition roleCreatedCondition = SECURITY_ROLE.CLIENT_ID.eq(clientId);

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_ROLE)
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_ROLE.ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
						.leftJoin(SECURITY_PACKAGE)
						.on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
						.where(roleExistsCondition.or(roleCreatedCondition)))

				.map(Record1::value1)
				.map(val -> val.intValue() > 0);
	}

	public Mono<Boolean> removePackage(ULong clientId, ULong packageId) {

		DeleteQuery<SecurityClientPackageRecord> query = this.dslContext.deleteQuery(SECURITY_CLIENT_PACKAGE);

		query.addConditions(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId)
				.and(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)));

		return Mono.from(query)
				.map(val -> val == 1);
	}

	public Mono<Package> getPackage(ULong packageId) {

		return Mono.from(this.dslContext.select(SECURITY_PACKAGE.fields())
				.from(SECURITY_PACKAGE)
				.where(SECURITY_PACKAGE.ID.eq(packageId))
				.limit(1))
				.filter(Objects::nonNull)
				.map(e -> e.into(Package.class));
	}

	public Mono<Role> getRole(ULong roleId) {

		return Mono.from(this.dslContext.select(SECURITY_ROLE.fields())
				.from(SECURITY_ROLE)
				.where(SECURITY_ROLE.ID.eq(roleId))
				.limit(1))
				.filter(Objects::nonNull)
				.map(e -> e.into(Role.class));
	}

	public Mono<Boolean> checkPackageAssignedForClient(ULong clientId, ULong packageId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_CLIENT_PACKAGE)
						.where(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)
								.and(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId))))
				.map(Record1::value1)
				.map(value -> value == 1);
	}

	public Mono<Boolean> checkRoleAssignedForClient(ULong clientId, ULong roleId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_CLIENT_PACKAGE)
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
						.where(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)
								.and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)))

		)
				.map(Record1::value1)
				.map(e -> e > 0);
	}

	public Mono<Boolean> findAndRemoveRolesFromUsers(List<ULong> roles, ULong packageId) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
				.deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.in(roles));

		return Flux.from(

				this.dslContext.selectDistinct(SECURITY_USER.ID)
						.from(SECURITY_PACKAGE_ROLE)
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
						.leftJoin(SECURITY_USER)
						.on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
						.where(SECURITY_PACKAGE_ROLE.ROLE_ID.in(roles)
								.and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.in(packageId))))
				.map(Record1::value1)
				.filter(Objects::nonNull)
				.collectList()
				.flatMap(userList -> {
					if (userList == null || userList.isEmpty())
						return Mono.just(false);

					query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(userList));

					return Mono.from(query)
							.map(value -> value == 1);
				});

	}

	public Mono<Boolean> findAndRemovePermissionsFromUsers(List<ULong> permissions, ULong packageId) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
				.deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.in(permissions));

		return Flux.from(

				this.dslContext.selectDistinct(SECURITY_USER.ID)
						.from(SECURITY_ROLE_PERMISSION)
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
						.leftJoin(SECURITY_USER)
						.on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
						.where(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissions)
								.and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId))))
				.map(Record1::value1)
				.collectList()
				.flatMap(userList -> {
					if (userList == null || userList.isEmpty())
						return Mono.just(false);

					query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(userList));

					return Mono.from(query)
							.map(value -> value > 0);
				});

	}

	public Flux<ClientUrlPattern> readClientPatterns() {

		return Flux
				.from(this.dslContext
						.select(SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT.CODE, SECURITY_CLIENT_URL.URL_PATTERN,
								SECURITY_CLIENT_URL.APP_CODE)
						.from(SECURITY_CLIENT_URL)
						.leftJoin(SECURITY_CLIENT)
						.on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID)))
				.map(e -> new ClientUrlPattern(e.value1()
						.toString(), e.value2(), e.value3(), e.value4()))
				.map(ClientUrlPattern::makeHostnPort);
	}

	public Mono<ULong> getManagingClientId(ULong clientId) {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID)
				.from(SECURITY_CLIENT_MANAGE)
				.where(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(clientId))
				.limit(1))
				.map(Record1::value1)
				.switchIfEmpty(Mono.defer(() ->

				Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
						.from(SECURITY_CLIENT)
						.where(SECURITY_CLIENT.TYPE_CODE.eq("SYS"))
						.limit(1))
						.map(Record1::value1)
						.flatMap(id -> id.equals(clientId) ? Mono.empty() : Mono.just(id))

				));
	}

	public Mono<String> getValidClientCode(String name) {

		name = removeSpecialCharacters(name);

		String clientCode = name.substring(0, name.length() < 5 ? name.length() : 5)
				.toUpperCase();

		return Flux.just(clientCode)
				.expand(e -> Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
						.from(SECURITY_CLIENT)
						.where(SECURITY_CLIENT.CODE.eq(e))
						.limit(1))
						.map(Record1::value1)
						.map(x -> {
							if (x.length() == clientCode.length())
								return clientCode + "1";

							int num = Integer.parseInt(x.substring(clientCode.length())) + 1;
							return clientCode + num;
						}))
				.collectList()
				.map(e -> e.get(e.size() - 1));

	}

	public Mono<List<Package>> getPackagesAvailableForClient(ULong clientId) {

		return Flux.from(

				this.dslContext.select(SECURITY_PACKAGE.fields())
						.from(SECURITY_CLIENT_PACKAGE)

						.leftJoin(SECURITY_PACKAGE)
						.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))

						.where(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(clientId)))
				.map(e -> e.into(Package.class))
				.collectList();
	}

	public Mono<List<ULong>> getManagingClientIds(List<ULong> list) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID)
				.from(SECURITY_CLIENT_MANAGE)
				.where(SECURITY_CLIENT_MANAGE.CLIENT_ID.in(list)))
				.map(Record1::value1)
				.collectList();
	}

	public Mono<ULong> getSystemClientId() {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
				.from(SECURITY_CLIENT)
				.where(SECURITY_CLIENT.TYPE_CODE.eq("SYS"))
				.limit(1))
				.map(Record1::value1);
	}

}
