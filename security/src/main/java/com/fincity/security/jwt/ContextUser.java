package com.fincity.security.jwt;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContextUser implements Serializable {

	private static final long serialVersionUID = -4905785598739255667L;

	private BigInteger id;
	private BigInteger createdBy;
	private BigInteger updatedBy;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private BigInteger clientId;
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
	private String statusCode;
	private List<String> authorities;
	private List<GrantedAuthority> grantedAuthorities;

	public Collection<GrantedAuthority> getAuthorities() {

		if (this.authorities == null || this.authorities.isEmpty())
			return List.of();

		if (this.grantedAuthorities == null) {
			this.grantedAuthorities = this.authorities.parallelStream()
			        .map(SimpleGrantedAuthority::new)
			        .map(GrantedAuthority.class::cast)
			        .toList();
		}
		return this.grantedAuthorities;
	}

	@JsonIgnore
	public String getPassword() {
		return this.password;
	}

	@JsonIgnore
	public boolean isPasswordHashed() {
		return this.passwordHashed;
	}
}
