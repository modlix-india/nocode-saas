package com.fincity.security.dto;

import java.io.Serial;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.security.jooq.enums.SecurityClientLevelType;
import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Client extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 4312344235572008119L;

    private String code;
    private String name;
    private String typeCode;
    private int tokenValidityMinutes;
    private String localeCode;
    private SecurityClientStatusCode statusCode;
    private String businessType;
    private String businessSize;
    private String industry;
    private SecurityClientLevelType levelType;

    private Integer activeUsers;
    private Integer inactiveUsers;
    private Integer deletedUsers;
    private Integer lockedUsers;
    private Integer passwordExpiredUsers;

    private List<User> owners;
    private Client managagingClient;
    private List<App> apps;
    private User createdByUser;


    public static SecurityClientLevelType getChildClientLevelType(SecurityClientLevelType level) {
        return switch (level) {
            case SYSTEM -> SecurityClientLevelType.CLIENT;
            case CLIENT -> SecurityClientLevelType.CUSTOMER;
            case CUSTOMER -> SecurityClientLevelType.CONSUMER;
            default -> null;
        };
    }

    public static SecurityClientLevelType getChildClientLevelType(String level) {
        return getChildClientLevelType(getClientLevelType(level));
    }

    public static SecurityClientLevelType getClientLevelType(String level) {
        return SecurityClientLevelType.valueOf(level);
    }

    @JsonProperty(value = "totalUsers")
    public Integer getTotalUsers() {
        if (activeUsers == null) return null;
        return activeUsers + inactiveUsers + deletedUsers + lockedUsers + passwordExpiredUsers;
    }
}
