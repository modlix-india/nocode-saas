package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityCodeAccess.SECURITY_CODE_ACCESS;

import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.util.CodeUtil;
import com.fincity.security.dto.CodeAccess;
import com.fincity.security.jooq.tables.SecurityCodeAccess;
import com.fincity.security.jooq.tables.records.SecurityCodeAccessRecord;

import reactor.core.publisher.Mono;

@Service
public class CodeAccessDAO extends AbstractDAO<SecurityCodeAccessRecord, ULong, CodeAccess> {

	private static final CodeUtil.CodeGenerationConfiguration CODEGEN_CONFIG = new CodeUtil.CodeGenerationConfiguration()
	        .setNumeric(true)
	        .setSeparators(new int[] { 4 })
	        .setSeparator("-")
	        .setLength(8);

	public CodeAccessDAO() {
		super(CodeAccess.class, SecurityCodeAccess.SECURITY_CODE_ACCESS, SecurityCodeAccess.SECURITY_CODE_ACCESS.ID);
	}

	@Override
	public Mono<CodeAccess> create(CodeAccess pojo) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () ->
				{

			        Condition condition = DSL.and(SECURITY_CODE_ACCESS.APP_ID.eq(pojo.getAppId()),
			                SECURITY_CODE_ACCESS.CLIENT_ID.eq(pojo.getClientId()),
			                SECURITY_CODE_ACCESS.EMAIL_ID.eq(pojo.getEmailId()));
			        return Mono.from(this.dslContext.selectFrom(SECURITY_CODE_ACCESS)
			                .where(condition)
			                .limit(1))
			                .map(e -> e.into(CodeAccess.class));
		        },

		        exists ->
				{

			        if (exists != null)
				        return Mono.just(exists);

			        return Mono.just(CodeUtil.generate(CODEGEN_CONFIG))
			                .expand(e ->
							{
				                Condition condition = DSL.and(SECURITY_CODE_ACCESS.APP_ID.eq(pojo.getAppId()),
				                        SECURITY_CODE_ACCESS.CLIENT_ID.eq(pojo.getClientId()),
				                        SECURITY_CODE_ACCESS.CODE.eq(e));
				                return Mono.from(this.dslContext.selectCount()
				                        .from(SECURITY_CODE_ACCESS)
				                        .where(condition)
				                        .limit(1))
				                        .map(Record1::value1)
				                        .flatMap(c ->
										{
					                        if (c == 0)
						                        return Mono.empty();
					                        return Mono.just(CodeUtil.generate(CODEGEN_CONFIG));
				                        });
			                })
			                .collectList()
			                .flatMap(e -> super.create(pojo.setCode(e.get(e.size() - 1))));
		        });
	}

	public Mono<Boolean> checkClientAccessCode(ULong appId, ULong clientId, String emailId, String accessCode) {


		return Mono.from(this.dslContext.selectCount()
		        .from(SECURITY_CODE_ACCESS)
		        .where(DSL.and(SECURITY_CODE_ACCESS.EMAIL_ID.eq(emailId), SECURITY_CODE_ACCESS.CODE.eq(accessCode),
		                SECURITY_CODE_ACCESS.APP_ID.eq(appId), SECURITY_CODE_ACCESS.CLIENT_ID.eq(clientId))))
		        .map(Record1::value1)

		        .map(val -> val == 1);
	}

	public Mono<Boolean> deleteRecordAfterRegistration(String appCode, ULong clientId, String emailId,
	        String accessCode) {

		Condition cond = DSL.and(SECURITY_CODE_ACCESS.EMAIL_ID.eq(emailId))
		        .and(SECURITY_CODE_ACCESS.CLIENT_ID.eq(clientId))
		        .and(SECURITY_CODE_ACCESS.CODE.eq(accessCode))
		        .and(SECURITY_CODE_ACCESS.APP_ID.eq(this.dslContext.select(SECURITY_APP.ID)
		                .from(SECURITY_APP)
		                .where(SECURITY_APP.APP_CODE.eq(appCode))));
		

		return Mono.from(this.dslContext.deleteFrom(SECURITY_CODE_ACCESS)
		        .where(cond))
		        .map(e -> e == 1);
	}
}
