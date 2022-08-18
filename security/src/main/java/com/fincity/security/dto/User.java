package com.fincity.security.dto;

import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jwt.ContextUser;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class User extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 754028768624617709L;

	public static final String PLACEHOLDER = "NONE";

	private ULong clientId;
	private String userName;
	private String emailId;
	private String phoneNumber;
	private String firstName;
	private String lastName;
	private String middleName;
	private String localeCode;
	private String password;
	private boolean passwordHashed;
	private boolean accountNonExpired;
	private boolean accountNonLocked;
	private boolean credentialsNonExpired;
	private Short noFailedAttempt;
	private SecurityUserStatusCode statusCode;
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

	@JsonIgnore
	public String getPassword() {
		return this.password;
	}

	@JsonIgnore
	public boolean isPasswordHashed() {
		return this.passwordHashed;
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
		        .setAuthorities(authorities)
		        .setClientId(safeFrom(this.clientId))
		        .setCredentialsNonExpired(credentialsNonExpired)
		        .setEmailId(this.getEmailId())
		        .setFirstName(firstName)
		        .setLastName(lastName)
		        .setLocaleCode(localeCode)
		        .setMiddleName(middleName)
		        .setNoFailedAttempt(noFailedAttempt)
		        .setPhoneNumber(this.getPhoneNumber())
		        .setStatusCode(this.statusCode.toString())
		        .setUserName(this.getUserName());
	}

	public static final BigInteger safeFrom(ULong v) {

		if (v == null)
			return null;

		return v.toBigInteger();
	}
}
