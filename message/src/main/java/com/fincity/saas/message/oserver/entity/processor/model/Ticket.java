package com.fincity.saas.message.oserver.entity.processor.model;

import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class Ticket implements Serializable {

    private BigInteger id;
    private String appCode;
    private String clientCode;
    private String code;
    private String name;
    private String description;
    private BigInteger ownerId;
    private BigInteger assignedUserId;
    private Integer dialCode;
    private String phoneNumber;
    private String email;
    private BigInteger productId;
    private BigInteger stage;
    private BigInteger status;
    private String source;
    private String subSource;
    private BigInteger campaignId;
    private Boolean dnc;
}
