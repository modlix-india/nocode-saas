package com.fincity.security.dao;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.Otp;
import static com.fincity.security.jooq.Tables.SECURITY_OTP;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;

import reactor.core.publisher.Mono;

@Component
public class OtpDAO extends AbstractDAO<SecurityOtpRecord, ULong, Otp> {

	protected OtpDAO() {
		super(Otp.class, SECURITY_OTP, SECURITY_OTP.ID);
	}

	public Mono<Otp> getLatestOtp(ULong appId, ULong userId, String purpose) {
		return Mono.from(this.dslContext.selectFrom(SECURITY_OTP)
				.where(
						SECURITY_OTP.APP_ID.eq(appId)
								.and(SECURITY_OTP.USER_ID.eq(userId))
								.and(SECURITY_OTP.PURPOSE.eq(purpose)))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(Otp.class));
	}

	public Mono<Otp> getLatestOtp(ULong appId, String emailId, String phoneNumber, String purpose) {
		return Mono.from(this.dslContext.selectFrom(SECURITY_OTP)
				.where(
						SECURITY_OTP.APP_ID.eq(appId)
								.and(SECURITY_OTP.EMAIL_ID.eq(emailId))
								.and(SECURITY_OTP.PHONE_NUMBER.eq(phoneNumber))
								.and(SECURITY_OTP.PURPOSE.eq(purpose)))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(Otp.class));
	}

	public Mono<String> getLatestOtpCode(ULong appId, ULong userId, String purpose) {
		return Mono.from(this.dslContext.select(SECURITY_OTP.UNIQUE_CODE).from(SECURITY_OTP)
				.where(
						SECURITY_OTP.APP_ID.eq(appId)
								.and(SECURITY_OTP.USER_ID.eq(userId))
								.and(SECURITY_OTP.PURPOSE.eq(purpose)))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(String.class));
	}

	public Mono<String> getLatestOtpCode(ULong appId, String emailId, String phoneNumber, String purpose) {
		return Mono.from(this.dslContext.select(SECURITY_OTP.UNIQUE_CODE).from(SECURITY_OTP)
				.where(
						SECURITY_OTP.APP_ID.eq(appId)
								.and(SECURITY_OTP.EMAIL_ID.eq(emailId))
								.and(SECURITY_OTP.PHONE_NUMBER.eq(phoneNumber))
								.and(SECURITY_OTP.PURPOSE.eq(purpose)))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(String.class));
	}

}
