package com.fincity.saas.common.security.jwt;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.common.security.util.RolePermissionUtil;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
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
	private List<String> stringAuthorities;
	@JsonIgnore
	private Set<SimpleGrantedAuthority> grantedAuthorities;

	@JsonIgnore
	public Collection<SimpleGrantedAuthority> getAuthorities() {

		if (this.stringAuthorities == null || this.stringAuthorities.isEmpty())
			return Set.of();

		if (this.grantedAuthorities == null) {
			this.grantedAuthorities = this.stringAuthorities.parallelStream()
			        .map(RolePermissionUtil::toAuthorityString)
			        .map(SimpleGrantedAuthority::new)
//			        .map(GrantedAuthority.class::cast)
			        .collect(Collectors.toSet());
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
