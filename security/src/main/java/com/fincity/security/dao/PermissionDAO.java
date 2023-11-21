package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.Permission;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class PermissionDAO extends AbstractClientCheckDAO<SecurityPermissionRecord, ULong, Permission> {

	protected PermissionDAO() {
		super(Permission.class, SECURITY_PERMISSION, SECURITY_PERMISSION.ID);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Page<Permission>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> joinStep;

					boolean isSystemClient = ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode());

					if (isSystemClient) {

						joinStep = this.getSelectJointStep();
					} else {

						SelectJoinStep<Record> queryJoinStep = this.dslContext.select(SECURITY_PERMISSION.fields())
								.from(SECURITY_PERMISSION)
								.leftJoin(SECURITY_ROLE_PERMISSION)
								.on(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(SECURITY_PERMISSION.ID))
								.leftJoin(SECURITY_PACKAGE_ROLE)
								.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
								.leftJoin(SECURITY_PACKAGE)
								.on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
								.leftJoin(SECURITY_CLIENT_PACKAGE)
								.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID));

						SelectJoinStep<Record1<Integer>> countJoinStep = this.dslContext.select(DSL.count())
								.from(SECURITY_PERMISSION)
								.leftJoin(SECURITY_ROLE_PERMISSION)
								.on(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(SECURITY_PERMISSION.ID))
								.leftJoin(SECURITY_PACKAGE_ROLE)
								.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
								.leftJoin(SECURITY_PACKAGE)
								.on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
								.leftJoin(SECURITY_CLIENT_PACKAGE)
								.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID));

						joinStep = Mono.just(Tuples.of(queryJoinStep, countJoinStep));
					}

					return joinStep.flatMap(selectJoinStepTuple -> {

						AbstractCondition finalCondition = condition;

						if (!isSystemClient) {

							ComplexCondition joinCondition = new ComplexCondition()
									.setOperator(ComplexConditionOperator.OR)
									.setConditions(List.of(
											new FilterCondition().setOperator(FilterConditionOperator.EQUALS)
													.setField("ClientPackage.clientId")
													.setValue(ca.getUser()
															.getClientId()
															.toString()),
											new FilterCondition().setOperator(FilterConditionOperator.EQUALS)
													.setField("Package.base")
													.setValue("1")));

							if (finalCondition == null)
								finalCondition = joinCondition;
							else
								finalCondition = new ComplexCondition().setOperator(ComplexConditionOperator.AND)
										.setConditions(List.of(finalCondition, joinCondition));

						}

						if (finalCondition != null) {
							return filter(finalCondition).flatMap(filterCondition -> list(pageable,
									selectJoinStepTuple.mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
											.mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(filterCondition))));
						}
						return list(pageable, selectJoinStepTuple);
					});

				});
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected Field getField(String fieldName) {

		if ("ClientPackage.clientId".equals(fieldName))
			return SECURITY_CLIENT_PACKAGE.CLIENT_ID;
		if ("Package.base".equals(fieldName))
			return SECURITY_PACKAGE.BASE;

		return super.getField(fieldName);
	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
				.map(ca -> {

					var cAlias = SECURITY_CLIENT.as("codeClient");

					SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields()))
							.select(DSL
									.replace(DSL.concat("Authorities.",
											DSL.concat(
													DSL.if_(SECURITY_APP.APP_CODE.isNull(), "",
															DSL.concat(DSL.upper(SECURITY_APP.APP_CODE), ".")),
													DSL.if_(cAlias.CODE.eq("SYSTEM"), SECURITY_PERMISSION.NAME,
															DSL.concat(DSL.concat(cAlias.CODE, "_"),
																	SECURITY_PERMISSION.NAME)))),
											" ", "_")
									.as("AUTHORITY"))
							.from(table)
							.leftJoin(cAlias)
							.on(cAlias.ID.eq(SECURITY_PERMISSION.CLIENT_ID))
							.leftJoin(SECURITY_APP)
							.on(SECURITY_APP.ID.eq(SECURITY_PERMISSION.APP_ID));

					SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count())
							.from(table);

					if (ca.getClientTypeCode()
							.equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
						return Tuples.of(mainQuery, countQuery);

					return this.addJoinCondition(mainQuery, countQuery, this.getClientIDField());
				});
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_PERMISSION.CLIENT_ID;
	}

	public Mono<List<Permission>> getPermissionsByNamesAndAppId(List<String> names, ULong appId) {

		return Flux.from(
				this.dslContext.selectFrom(SECURITY_PERMISSION)
						.where(SECURITY_PERMISSION.NAME.in(names).and(SECURITY_PERMISSION.APP_ID.eq(appId))))
				.map(e -> e.into(Permission.class))
				.collectList();
	}

	public Mono<List<Permission>> createPermissionsFromTransport(List<Permission> permissions) {

		return Flux.fromIterable(permissions).flatMap(this::create).collectList();
	}

	public Mono<Boolean> createRolePermissions(Map<ULong, ULong> rolePermissions) {

		return FlatMapUtil.flatMapMono(

				() -> Mono.from(this.dslContext.deleteFrom(SECURITY_ROLE_PERMISSION)
						.where(SECURITY_ROLE_PERMISSION.ROLE_ID.in(rolePermissions.keySet()))),

				delCount -> Mono.from(
						this.dslContext
								.insertInto(SECURITY_ROLE_PERMISSION, SECURITY_ROLE_PERMISSION.ROLE_ID,
										SECURITY_ROLE_PERMISSION.PERMISSION_ID)
								.values(rolePermissions.entrySet().stream()
										.map(e -> DSL.row(e.getKey(), e.getValue())).toList()))
						.map(e -> true));
	}
}
