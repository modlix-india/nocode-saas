package com.fincity.saas.commons.security.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ClientDenormData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clientName;
    private String statusCode;
    private int activeUsers;
    private String userNames;
    private String userPhones;
    private String clientManagerIds;
}
