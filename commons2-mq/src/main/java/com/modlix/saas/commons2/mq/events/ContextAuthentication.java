package com.modlix.saas.commons2.mq.events;

import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContextAuthentication implements Serializable {

    private static final long serialVersionUID = 1127850908587759885L;

    private String user;
    private boolean isAuthenticated;
    private String clientCode;
    private String appCode;
    private String accessToken;
}
