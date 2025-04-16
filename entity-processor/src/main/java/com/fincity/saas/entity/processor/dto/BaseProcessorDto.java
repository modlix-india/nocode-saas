package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseProcessorDto<T extends BaseProcessorDto<T>> extends AbstractFlowUpdatableDTO<ULong, ULong> {

    @Version
    private int version = 1;

    private String code = UniqueUtil.shortUUID();

    private String name;
    private String description;
    private ULong addedByUserId;
    private ULong currentUserId;
    private String status;
    private String subStatus;

    private boolean tempActive = Boolean.FALSE;
    private boolean isActive = Boolean.TRUE;

    public T setCode() {

        if (this.code != null) return (T) this;

        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }
}
