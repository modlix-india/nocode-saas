package com.fincity.saas.commons.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

import com.fincity.saas.commons.util.IClassConvertor;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class Profile implements Serializable, IClassConvertor {

    @Serial
    private static final long serialVersionUID = 3777687266334703172L;

    private BigInteger id;
    private BigInteger appId;
    private BigInteger clientId;
    private String name;
    private String title;
    private String description;
    private boolean defaultProfile;
    private BigInteger rootProfileId;
    private Map<String, Object> arrangement;
}
