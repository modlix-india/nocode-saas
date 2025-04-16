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
public class Model extends BaseProcessorDto<Model> {

    @Serial
    private static final long serialVersionUID = 884230109954534627L;

    private String dialCode;
    private String phoneNumber;
    private String email;
    private String source;
    private String subSource;
}
