package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.SECURITY_OTP;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.security.dto.Otp;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;

import reactor.core.publisher.Mono;

@Component
public class OtpDAO extends AbstractDAO<SecurityOtpRecord, ULong, Otp> {

	protected OtpDAO() {
		super(Otp.class, SECURITY_OTP, SECURITY_OTP.ID);
	}

	public Mono<Otp> getLatestOtp(ULong appId, ULong userId, String purpose, SecurityOtpTargetType targetType) {

		return this.filter(ComplexCondition.and(
						FilterCondition.make(SECURITY_OTP.APP_ID.getName(), appId),
						FilterCondition.make(SECURITY_OTP.USER_ID.getName(), userId),
						FilterCondition.make(SECURITY_OTP.PURPOSE.getName(), purpose),
						FilterCondition.make(SECURITY_OTP.TARGET_TYPE.getName(), targetType.getName())
				)).flatMap(condition ->
						Mono.from(this.dslContext.selectFrom(SECURITY_OTP.getName()).where(condition)
								.orderBy(SECURITY_OTP.CREATED_AT.desc()).limit(1)))
				.map(e -> e.into(Otp.class));
	}
}
