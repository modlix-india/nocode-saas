package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityCodeAccess.SECURITY_CODE_ACCESS;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.security.dto.CodeAccess;
import com.fincity.security.jooq.tables.SecurityCodeAccess;
import com.fincity.security.jooq.tables.records.SecurityCodeAccessRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CodeAccessDAO extends AbstractDAO<SecurityCodeAccessRecord, ULong, CodeAccess> {

	public CodeAccessDAO() {
		super(CodeAccess.class, SecurityCodeAccess.SECURITY_CODE_ACCESS, SecurityCodeAccess.SECURITY_CODE_ACCESS.ID);
	}

	public Mono<Boolean> createRecordWithEmail(CodeAccess codeAccess, String appCode, String clientCode) {

		Condition appAndClientCode = SECURITY_CLIENT.CODE.eq(clientCode)
		        .and(SECURITY_APP.APP_CODE.eq(appCode));

		return Mono.from(this.dslContext.select(SECURITY_APP.ID, SECURITY_CLIENT.ID)
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(appAndClientCode)
		        .limit(1))
		        .flatMap(e ->
				{

			        return Mono.from(this.dslContext
			                .insertInto(SECURITY_CODE_ACCESS, SECURITY_CODE_ACCESS.EMAIL_ID, SECURITY_CODE_ACCESS.CODE,
			                        SECURITY_CODE_ACCESS.APP_ID, SECURITY_CODE_ACCESS.CLIENT_ID)
			                .values(codeAccess.getEmailId(), codeAccess.getCode(),
			                        ULongUtil.valueOf(e.get(SECURITY_APP.ID)),
			                        ULongUtil.valueOf(e.get(SECURITY_CLIENT.ID))));

		        })
		        .map(e -> e > 0);
	}

	public Mono<Boolean> checkClientAccess(String clientCode, String appCode, String emailId) {

		Condition appAndClientCode = SECURITY_CLIENT.CODE.eq(clientCode)
		        .and(SECURITY_APP.APP_CODE.eq(appCode));

		return Mono.from(this.dslContext.select(SECURITY_APP.ID, SECURITY_CLIENT.ID)
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(appAndClientCode)
		        .limit(1))
		        .flatMap(res -> Mono.from(this.dslContext.selectCount()
		                .from(SECURITY_CODE_ACCESS)
		                .where(SECURITY_CODE_ACCESS.EMAIL_ID.eq(emailId))))
		        .map(Record1::value1)
		        .map(e -> e == 1);
	}

	public Mono<List<CodeAccess>> getRecordsByAppAndClientCodes(String appCode, String clientCode, String emailId) {

		Condition appAndClientCode = SECURITY_CLIENT.CODE.eq(clientCode)
		        .and(SECURITY_APP.APP_CODE.eq(appCode));

		return Mono.from(this.dslContext.select(SECURITY_APP.ID, SECURITY_CLIENT.ID)
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(appAndClientCode)
		        .limit(1))
		        .flatMap(res -> Flux.from(this.dslContext.select(SECURITY_CODE_ACCESS.fields())
		                .from(SECURITY_CODE_ACCESS)
		                .where(SECURITY_CODE_ACCESS.EMAIL_ID.like("%" + emailId + "%")))
		                .map(e -> e.into(CodeAccess.class))
		                .collectList());

	}

}
