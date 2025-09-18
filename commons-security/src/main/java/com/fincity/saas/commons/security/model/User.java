package com.fincity.saas.commons.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.util.IClassConvertor;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class User implements Serializable, IClassConvertor {

    @Serial
    private static final long serialVersionUID = 5600650589811219972L;

    private BigInteger id;
    private BigInteger clientId;
    private String userName;
    private String emailId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String middleName;
    private String localeCode;
    private String statusCode;

    private List<Profile> profiles;
    private Client client;
    private Client managingClient;
    private User createdByUser;
}
