package com.fincity.security.model;

import lombok.Data;

@Data
public class MakeOneTimeTimeTokenRequest {

    private String callbackUrl;
    private boolean rememberMe = false;
    private String targetAppCode;
    private String targetClientCode;
}
