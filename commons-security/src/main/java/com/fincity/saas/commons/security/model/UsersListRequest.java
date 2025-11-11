package com.fincity.saas.commons.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UsersListRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1868750889822071718L;

    private List<Long> userIds;
    private String clientCode;
    private Long clientId;
    private String appCode;
}
