package com.fincity.security.model;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.security.dto.Client;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationResponse implements Serializable {

    private ContextUser user;
    private Client client;
    private String verifiedAppCode;
    private String loggedInClientCode;
    private BigInteger loggedInClientId;

    private String accessToken;
    private LocalDateTime accessTokenExpiryAt;

    public ContextAuthentication makeContextAuthentication() {

        return new ContextAuthentication()
                .setAccessToken(this.accessToken)
                .setUser(this.user)
                .setVerifiedAppCode(this.verifiedAppCode)
                .setLoggedInFromClientCode(this.loggedInClientCode)
                .setLoggedInFromClientId(this.loggedInClientId)
                .setAccessTokenExpiryAt(this.accessTokenExpiryAt);
    }
}
