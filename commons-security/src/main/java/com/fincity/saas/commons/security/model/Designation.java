package com.fincity.saas.commons.security.model;

import com.fincity.saas.commons.util.IClassConvertor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

import java.io.Serializable;
import java.math.BigInteger;



@Data
@Accessors(chain = true)
@FieldNameConstants
public class Designation implements Serializable, IClassConvertor {
    private BigInteger id;
    private BigInteger clientId;
    private String name;
    private String description;
    private BigInteger parentDesignationId;
    private BigInteger departmentId;
    private BigInteger nextDesignationId;
    private BigInteger profileId;
    private Designation parentDesignation;
    private Designation nextDesignation;
}
