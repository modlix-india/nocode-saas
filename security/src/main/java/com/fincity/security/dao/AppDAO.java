package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.tables.records.SecurityAppAccessRecord;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class AppDAO extends AbstractUpdatableDAO<SecurityAppRecord, ULong, App> {

	protected AppDAO() {
		super(App.class, SECURITY_APP, SECURITY_APP.ID);
	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ca ->
				{

			        SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields()))
			                .from(table);

			        SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count())
			                .from(table);

			        if (ca.getClientTypeCode()
			                .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
				        return Tuples.of(mainQuery, countQuery);

			        return Tuples.of((SelectJoinStep<Record>) mainQuery.leftJoin(SECURITY_APP_ACCESS)
			                .on(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)
			                        .and(SECURITY_APP_ACCESS.CLIENT_ID.eq(ULong.valueOf(ca.getUser()
			                                .getClientId())))),
			                (SelectJoinStep<Record1<Integer>>) countQuery.leftJoin(SECURITY_APP_ACCESS)
			                        .on(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)
			                                .and(SECURITY_APP_ACCESS.CLIENT_ID.eq(ULong.valueOf(ca.getUser()
			                                        .getClientId())))));
		        });
	}

	@Override
	protected Mono<Condition> filter(AbstractCondition acond) {

		Mono<Condition> condition = super.filter(acond);

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
			        if (ca.isSystemClient())
				        return condition;

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());

			        return condition.map(c -> DSL.and(c, SECURITY_APP.CLIENT_ID.eq(clientId)
			                .or(SECURITY_APP_ACCESS.CLIENT_ID.eq(clientId)
			                        .and(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf((byte) 1))))));
		        })
		        .switchIfEmpty(condition);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {

		return hasOnlyInternalAccess(appCode, clientCode, 1);
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
		return hasOnlyInternalAccess(appCode, clientCode, 0);
	}

	private Mono<Boolean> hasOnlyInternalAccess(String appCode, String clientCode, int accessType) {

		List<Condition> conditions = new ArrayList<>();
		conditions.add(SECURITY_CLIENT.CODE.eq(clientCode));
		if (accessType == 1) {
			conditions.add(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1)));
		}

		SelectConditionStep<Record1<ULong>> inQuery = this.dslContext.select(SECURITY_APP_ACCESS.APP_ID)
		        .from(SECURITY_APP_ACCESS)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP_ACCESS.CLIENT_ID))
		        .where(DSL.and(conditions));

		return Mono.from(this.dslContext.select(DSL.count())
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
		        .where(SECURITY_APP.APP_CODE.eq(appCode)
		                .and(SECURITY_CLIENT.CODE.eq(clientCode)
		                        .or(SECURITY_APP.ID.in(inQuery))))
		        .limit(1))
		        .map(Record1::value1)
		        .map(e -> e != 0);
	}

	public Mono<Boolean> addClientAccess(ULong appId, ULong clientId, boolean writeAccess) {

		UByte edit = UByte.valueOf(writeAccess ? 1 : 0);

		return SecurityContextUtil.getUsersContextUser()
		        .map(ContextUser::getId)
		        .map(ULong::valueOf)
		        .flatMap(userId -> Mono.fromCompletionStage(this.dslContext.insertInto(SECURITY_APP_ACCESS)
		                .columns(SECURITY_APP_ACCESS.APP_ID, SECURITY_APP_ACCESS.CLIENT_ID,
		                        SECURITY_APP_ACCESS.EDIT_ACCESS, SECURITY_APP_ACCESS.CREATED_BY)
		                .values(appId, clientId, edit, userId)
		                .onDuplicateKeyUpdate()
		                .set(SECURITY_APP_ACCESS.EDIT_ACCESS, edit)
		                .set(SECURITY_APP_ACCESS.UPDATED_BY, userId)
		                .executeAsync()))
		        .map(e -> e == 1);
	}

	public Mono<Boolean> removeClientAccess(ULong appId, ULong accessId) {

		return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_ACCESS)
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)
		                .and(SECURITY_APP_ACCESS.APP_ID.eq(appId))))
		        .map(e -> e == 1);
	}

	public Mono<Boolean> updateClientAccess(ULong accessId, boolean writeAccess) {

		return Mono.from(this.dslContext.update(SECURITY_APP_ACCESS)
		        .set(SECURITY_APP_ACCESS.EDIT_ACCESS, UByte.valueOf(writeAccess ? 1 : 0))
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)))
		        .map(e -> e == 1);
	}

	public Mono<SecurityAppAccessRecord> readClientAccess(ULong accessId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACCESS)
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)));
	}

	public Mono<List<String>> appInheritance(String appCode, String clientCode) {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .limit(1))
		        .map(Record1::value1)
		        .map(code -> clientCode.equals(code) ? List.of(code) : List.of(code, clientCode));
	}

	public Flux<ULong> getClientIdsWithWriteAccess(String appCode) {

		return Flux.from(this.dslContext.select(SECURITY_APP.CLIENT_ID)
		        .from(SECURITY_APP)
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .union(this.dslContext.select(SECURITY_APP_ACCESS.CLIENT_ID)
		                .from(SECURITY_APP_ACCESS)
		                .leftJoin(SECURITY_APP)
		                .on(SECURITY_APP.ID.eq(SECURITY_APP_ACCESS.APP_ID))
		                .where(SECURITY_APP.APP_CODE.eq(appCode)
		                        .and(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1))))))
		        .map(Record1::value1)
		        .distinct()
		        .sort();
	}

	public Mono<App> getByAppCode(String appCode) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_APP)
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .limit(1))
		        .map(e -> e.into(App.class));
	}
}
