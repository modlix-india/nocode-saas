package com.fincity.security.dao;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.Otp;
import com.fincity.security.enums.otp.OtpPurpose;
import static com.fincity.security.jooq.Tables.SECURITY_OTP;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;

import reactor.core.publisher.Mono;

@Component
public class OtpDAO extends AbstractUpdatableDAO<SecurityOtpRecord, ULong, Otp> {

	protected OtpDAO() {
		super(Otp.class, SECURITY_OTP, SECURITY_OTP.ID);
	}

	public Mono<Boolean> decreaseVerifyCounts(ULong otpId) {

		return Mono.from(this.dslContext.select(SECURITY_OTP.VERIFY_LEGS_COUNTS)
						.from(SECURITY_OTP)
						.where(SECURITY_OTP.ID.eq(otpId)))
				.flatMap(count -> {
					if (count == null || count.value1() == 0)
						return Mono.empty();

					return Mono.from(this.dslContext.update(SECURITY_OTP)
									.set(SECURITY_OTP.VERIFY_LEGS_COUNTS, SECURITY_OTP.VERIFY_LEGS_COUNTS.sub(1))
									.where(SECURITY_OTP.ID.eq(otpId)))
							.map(rowsUpdated -> rowsUpdated > 0);
				});
	}

	public Mono<Otp> getLatestOtp(ULong appId, ULong userId, OtpPurpose purpose) {

		if (userId == null)
			return Mono.empty();

		return Mono.from(this.dslContext.selectFrom(SECURITY_OTP)
				.where(this.getUserOtpConditions(appId, userId, purpose))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(Otp.class));
	}

	public Mono<Otp> getLatestOtp(ULong appId, String emailId, String phoneNumber, OtpPurpose purpose) {

		if (StringUtil.safeIsBlank(emailId) && StringUtil.safeIsBlank(phoneNumber))
			return Mono.empty();

		return Mono.from(this.dslContext.selectFrom(SECURITY_OTP)
				.where(this.getWithoutUserOtpConditions(appId, emailId, phoneNumber, purpose))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(Otp.class));
	}

	public Mono<String> getLatestOtpCode(ULong appId, ULong userId, OtpPurpose purpose) {

		if (userId == null)
			return Mono.empty();

		return Mono.from(this.dslContext.select(SECURITY_OTP.UNIQUE_CODE).from(SECURITY_OTP)
				.where(this.getUserOtpConditions(appId, userId, purpose))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(String.class));
	}

	public Mono<String> getLatestOtpCode(ULong appId, String emailId, String phoneNumber, OtpPurpose purpose) {

		if (StringUtil.safeIsBlank(emailId) && StringUtil.safeIsBlank(phoneNumber))
			return Mono.empty();

		return Mono.from(this.dslContext.select(SECURITY_OTP.UNIQUE_CODE).from(SECURITY_OTP)
				.where(this.getWithoutUserOtpConditions(appId, emailId, phoneNumber, purpose))
				.orderBy(SECURITY_OTP.CREATED_AT.desc())
				.limit(1)).map(e -> e.into(String.class));
	}

	private List<Condition> getUserOtpConditions(ULong appId, ULong userId, OtpPurpose purpose) {

		List<Condition> conditions = getDefaultOtpConditions(appId, purpose);

		conditions.add(SECURITY_OTP.USER_ID.eq(userId));

		return conditions;
	}

	private List<Condition> getWithoutUserOtpConditions(ULong appId, String emailId, String phoneNumber,
			OtpPurpose purpose) {

		List<Condition> conditions = getDefaultOtpConditions(appId, purpose);

		if (!StringUtil.safeIsBlank(emailId))
			conditions.add(SECURITY_OTP.EMAIL_ID.eq(emailId));

		if (!StringUtil.safeIsBlank(phoneNumber))
			conditions.add(SECURITY_OTP.PHONE_NUMBER.eq(phoneNumber));

		return conditions;
	}

	private List<Condition> getDefaultOtpConditions(ULong appId, OtpPurpose purpose) {

		List<Condition> conditions = new ArrayList<>();

		conditions.add(SECURITY_OTP.APP_ID.eq(appId));
		conditions.add(SECURITY_OTP.PURPOSE.eq(purpose.name()));

		return conditions;
	}

}
