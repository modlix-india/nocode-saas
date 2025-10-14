package com.fincity.saas.commons.security.model;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NotificationUser {
    
    private Long id;
    private Long clientId;
    private String emailId;
}
