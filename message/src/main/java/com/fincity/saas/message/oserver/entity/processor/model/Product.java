package com.fincity.saas.message.oserver.entity.processor.model;

import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class Product implements Serializable, IClassConvertor {

    private BigInteger id;
    private String appCode;
    private String clientCode;
    private String code;
    private String name;
    private String description;
    private boolean isActive;
    private BigInteger clientId;
    private Boolean forPartner;
    private FileDetail logoFileDetail;
    private FileDetail bannerFileDetail;
}
