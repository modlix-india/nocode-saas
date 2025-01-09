package com.fincity.security.dto;

import java.io.Serial;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class User extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 754028768624617709L;

	public static final String PLACEHOLDER = "NONE";

	private ULong clientId;
	private String clientCode;
	private String userName;
	private String emailId;
	private String phoneNumber;
	private String firstName;
	private String lastName;
	private String middleName;
	private String localeCode;
	private String password;
	private boolean passwordHashed;
	private String pin;
	private boolean pinHashed;
	private boolean accountNonExpired;
	private boolean accountNonLocked;
	private boolean credentialsNonExpired;
	private Short noFailedAttempt;
	private Short noPinFailedAttempt;
	private Short noOtpResendAttempts;
	private Short noOtpFailedAttempt;
	private SecurityUserStatusCode statusCode;
	private LocalDateTime lockedUntil;
	private String lockedDueTo;
	private List<String> authorities;

	public String getUserName() {
		return PLACEHOLDER.equals(this.userName) ? null : this.userName;
	}

	public String getEmailId() {
		return PLACEHOLDER.equals(this.emailId) ? null : this.emailId;
	}

	public String getPhoneNumber() {
		return PLACEHOLDER.equals(this.phoneNumber) ? null : this.phoneNumber;
	}

	public boolean checkIdentificationKeys() {
		return PLACEHOLDER.equals(this.userName)
				&& PLACEHOLDER.equals(this.emailId)
				&& PLACEHOLDER.equals(this.phoneNumber);
	}

	@JsonIgnore
	public String getPassword() {
		return this.password;
	}

	@JsonIgnore
	public boolean isPasswordHashed() {
		return this.passwordHashed;
	}

	@JsonIgnore
	public String getPin() {
		return this.pin;
	}

	@JsonIgnore
	public boolean isPinHashed() {
		return this.pinHashed;
	}

	@JsonIgnore
	public ContextUser toContextUser() {
		return new ContextUser().setId(safeFrom(this.getId()))
				.setCreatedBy(safeFrom(this.getCreatedBy()))
				.setUpdatedBy(safeFrom(this.getUpdatedBy()))
				.setCreatedAt(this.getCreatedAt())
				.setUpdatedAt(this.getUpdatedAt())
				.setAccountNonExpired(this.accountNonExpired)
				.setAccountNonLocked(this.accountNonLocked)
				.setStringAuthorities(authorities)
				.setClientId(safeFrom(this.clientId))
				.setCredentialsNonExpired(credentialsNonExpired)
				.setEmailId(this.getEmailId())
				.setFirstName(firstName)
				.setLastName(lastName)
				.setLocaleCode(localeCode)
				.setMiddleName(middleName)
				.setNoFailedAttempt(noFailedAttempt)
				.setNoPinFailedAttempt(noPinFailedAttempt)
				.setNoOtpResendAttempts(noOtpResendAttempts)
				.setNoOtpFailedAttempt(noOtpFailedAttempt)
				.setPhoneNumber(this.getPhoneNumber())
				.setStatusCode(this.statusCode.toString())
				.setLockedUntil(lockedUntil)
				.setLockedDueTo(lockedDueTo)
				.setUserName(this.getUserName());
	}

	public static BigInteger safeFrom(ULong v) {

		if (v == null)
			return null;

		return v.toBigInteger();
	}
}
