package com.fincity.saas.entity.processor.dto.content.base;

import java.io.Serial;

import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;

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
public class BaseContentDto<T extends BaseContentDto<T>> extends BaseDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 5174424228629814984L;

    @Version
    private int version = 1;

    private String content;
    private Boolean hasAttachment;
    private ULong ownerId;
    private ULong ticketId;
}
