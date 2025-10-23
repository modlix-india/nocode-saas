package com.modlix.saas.commons2.security.model;

import java.util.List;

import lombok.Data;

@Data
public class UsersListRequest {
    private List<Long> userIds;
    private String clientCode;
    private Long clientId;
    private String appCode;
}
