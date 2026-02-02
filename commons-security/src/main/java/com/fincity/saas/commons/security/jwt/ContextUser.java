package com.fincity.saas.commons.security.jwt;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
public class ContextUser implements Serializable {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ContextUser.class);

    @Serial
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
    private String pin;
    private boolean pinHashed;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private Short noFailedAttempt;
    private Short noPinFailedAttempt;
    private Short noOtpResendAttempts;
    private Short noOtpFailedAttempt;
    private String statusCode;
    private LocalDateTime lockedUntil;
    private String lockedDueTo;
    private List<String> stringAuthorities;
	private BigInteger designationId;

    @JsonIgnore
    private Set<SimpleGrantedAuthority> grantedAuthorities;

    @JsonIgnore
    public Collection<SimpleGrantedAuthority> getAuthorities() {

        if (this.grantedAuthorities == null && this.stringAuthorities == null)
            logger.error("Danger!, Will Robinson. No Authorities found. {}", this);

        if (this.grantedAuthorities != null && !this.grantedAuthorities.isEmpty())
            return this.grantedAuthorities;

        if (this.stringAuthorities == null || this.stringAuthorities.isEmpty())
            return Set.of();

        if (this.grantedAuthorities == null) {
            this.grantedAuthorities = this.stringAuthorities.parallelStream()
                    .map(SimpleGrantedAuthority::new)
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

    @JsonIgnore
    public String getPin() {
        return this.pin;
    }

    @JsonIgnore
    public boolean isPinHashed() {
        return this.pinHashed;
    }

    public ContextUser setStringAuthorities(List<String> stringAuthorities) {
        this.stringAuthorities = stringAuthorities;
        return this;
    }
}
