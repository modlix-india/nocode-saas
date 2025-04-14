package com.fincity.saas.entity.processor.dto;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Entity extends BaseProcessorDto<Entity> {

    @Serial
    private static final long serialVersionUID = 7148027392286233787L;

    private String dialCode;
    private String phoneNumber;
    private String email;
    private ULong productId;
    private String source;
    private String subSource;
}
