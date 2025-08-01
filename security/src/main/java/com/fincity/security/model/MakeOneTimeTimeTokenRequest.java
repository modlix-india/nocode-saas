package com.fincity.security.model;

import lombok.Data;

@Data
public class MakeOneTimeTimeTokenRequest {

    private String callbackUrl;
}
