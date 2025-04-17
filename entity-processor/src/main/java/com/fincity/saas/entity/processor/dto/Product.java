package com.fincity.saas.entity.processor.dto;

import java.io.Serial;
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
public class Product extends BaseProcessorDto<Product> {

    @Serial
    private static final long serialVersionUID = 8003842565130760738L;
}
