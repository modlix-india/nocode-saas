package com.fincity.saas.entity.processor.dto.base;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
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
public class BaseDto<T extends BaseDto<T>> extends AbstractFlowUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1844345864104376760L;

    private String code = UniqueUtil.shortUUID();

    private String name = this.code;
    private String description;
    private ULong addedByUserId;

    private boolean tempActive = Boolean.FALSE;
    private boolean isActive = Boolean.TRUE;

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }

    public T setName(String name) {
        if (name == null || name.isBlank()) this.name = this.getCode();
        else this.name = name.trim();

        return (T) this;
    }

    public T setDescription(String description) {
        this.description = description.trim();
        return (T) this;
    }

    public T setAddedByUserId(ULong addedByUserId) {
        this.addedByUserId = addedByUserId;
        return (T) this;
    }

    public T setActive(boolean isActive) {
        this.isActive = isActive;
        return (T) this;
    }

    public T setTempActive(boolean tempActive) {
        this.tempActive = tempActive;
        return (T) this;
    }

    public BaseResponse toBaseResponse() {
        return BaseResponse.of(this.getId(), this.code, this.name);
    }
}
