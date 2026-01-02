package com.fincity.saas.commons.security.model;

import com.fincity.saas.commons.util.IClassConvertor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;



@Data
@Accessors(chain = true)
@FieldNameConstants
public class Designation implements Serializable, IClassConvertor {

    @Serial
    private static final long serialVersionUID = 3777687266334703172L;

    private BigInteger id;
    private BigInteger clientId;
    private String name;
    private String description;
    private BigInteger parentDesignationId;
    private BigInteger departmentId;
}
