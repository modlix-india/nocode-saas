package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;

import java.util.List;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.security.dto.Permission;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class PermissionDAO extends AbstractUpdatableDAO<SecurityPermissionRecord, ULong, Permission> {

	protected PermissionDAO() {
		super(Permission.class, SECURITY_PERMISSION, SECURITY_PERMISSION.ID);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Page<Permission>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

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
}
