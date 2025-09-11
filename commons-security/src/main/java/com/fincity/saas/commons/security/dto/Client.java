package com.fincity.saas.commons.security.dto;

import java.io.Serializable;
import java.math.BigInteger;

import com.fincity.saas.commons.util.IClassConvertor;

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
}
