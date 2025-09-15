package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.util.PhoneUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProductComm extends BaseProcessorDto<ProductComm> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private String connectionName;
    private String connectionType;
    private ULong productId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private boolean isDefault = false;

    public ProductComm() {
        super();
    }

    public ProductComm(ProductComm productComm) {
        super(productComm);
        this.connectionName = productComm.connectionName;
        this.connectionType = productComm.connectionType;
        this.productId = productComm.productId;
        this.dialCode = productComm.dialCode;
        this.phoneNumber = productComm.phoneNumber;
        this.email = productComm.email;
        this.isDefault = productComm.isDefault;
    }
}
