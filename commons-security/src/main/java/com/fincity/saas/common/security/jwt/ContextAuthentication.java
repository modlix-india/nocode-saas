package com.fincity.saas.common.security.jwt;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContextAuthentication implements Authentication {

	public static final String CLIENT_TYPE_SYSTEM = "SYS";

	private static final long serialVersionUID = 1127850908587759885L;

	private final ContextUser user;
	private boolean isAuthenticated;
	private final BigInteger loggedInFromClientId;
	private String clientTypeCode;
	private String clientCode;
	private String accessToken;
	private LocalDateTime accessTokenExpiryAt;

	@Override
	public String getName() {
		if (user == null)
			return null;
		return user.getFirstName();
	}

	@JsonIgnore
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (user == null)
			return List.of();
		return user.getAuthorities();
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getDetails() {
		return null;
	}

	@JsonIgnore
	@Override
	public Object getPrincipal() {
		return user;
	}

	public boolean isSystemClient() {

		return CLIENT_TYPE_SYSTEM.equals(this.clientTypeCode);
	}
}
