package com.fincity.security.model;

import lombok.Data;

@Data
public class MakeOneTimeTimeTokenRequest {

    private String callbackUrl;
    private boolean rememberMe = false;
    private String targetAppCode;
    private String targetClientCode;

    /**
     * {@code COOKIE} or {@code BEARER}: how the session this token is redeemed for should be
     * carried on the target origin.
     *
     * <p>Optional, and the only way to say. Left unset, the mode is inferred from how the
     * minting call itself authenticated, which is not the same thing as anybody choosing: an app
     * that sets an auth cookie at login mints COOKIE tokens forever after, the redeem sets
     * another cookie on the target origin, and that origin's next mint is COOKIE too. Nothing in
     * that chain expressed an intent, and until this field there was no way to express one.
     */
    private String authMode;
}
