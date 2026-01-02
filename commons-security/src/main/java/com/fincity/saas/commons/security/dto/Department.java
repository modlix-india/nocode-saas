package com.fincity.saas.commons.security.dto;

import java.io.Serializable;
import java.math.BigInteger;

import com.fincity.saas.commons.util.IClassConvertor;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class Department implements Serializable, IClassConvertor {

    private BigInteger id;
    private BigInteger clientId;
    private String name;
    private String description;
    private BigInteger parentDepartmentId;

    private Department parentDepartment;
}
