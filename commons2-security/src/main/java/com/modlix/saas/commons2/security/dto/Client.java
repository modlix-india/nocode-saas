package com.modlix.saas.commons2.security.dto;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.modlix.saas.commons2.security.model.User;
import com.modlix.saas.commons2.util.IClassConvertor;

import lombok.Data;

@Data
public class Client implements Serializable, IClassConvertor {

    private BigInteger id;
    private BigInteger createdBy;
    private BigInteger updatedBy;
    private String code;
    private String name;
    private String typeCode;
    private int tokenValidityMinutes;
    private String localeCode;
    private String statusCode;
    private String businessType;
    private String businessSize;
    private String industry;
    private String levelType;
    private int activeUsers;
    private int inactiveUsers;
    private int deletedUsers;
    private int lockedUsers;
    private int passwordExpiredUsers;

    private List<User> owners;
    private Client managagingClient;
    private List<App> apps;
    private User createdByUser;
    private List<User> clientManagers;

    @JsonProperty(value = "totalUsers")
    public Integer getTotalUsers() {
        return activeUsers + inactiveUsers + deletedUsers + lockedUsers + passwordExpiredUsers;
    }
}
