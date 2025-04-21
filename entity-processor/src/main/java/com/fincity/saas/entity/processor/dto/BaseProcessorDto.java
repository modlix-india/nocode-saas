package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

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
public abstract class BaseProcessorDto<T extends BaseProcessorDto<T>> extends BaseDto<BaseProcessorDto<T>>{

    @Serial
    private static final long serialVersionUID = 5174424228629814984L;

    @Version
    private int version = 1;

    private ULong currentUserId;
    private String status;
    private String subStatus;
}
