package com.fincity.saas.commons.security.model;

import java.io.Serial;
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

    @Serial
    private static final long serialVersionUID = 7698050889822071798L;

    private BigInteger id;
    private BigInteger clientId;
    private String name;
    private String description;
    private BigInteger parentDepartmentId;

    private Department parentDepartment;
}
