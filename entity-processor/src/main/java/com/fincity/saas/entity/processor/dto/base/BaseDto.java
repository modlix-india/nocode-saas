package com.fincity.saas.entity.processor.dto.base;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
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
public abstract class BaseDto<T extends BaseDto<T>> extends AbstractFlowDTO<ULong, ULong> {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 717874495505090612L;

    private String code = UniqueUtil.shortUUID();

    private String name = this.code;
    private String description;

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }

    public T setName(String name) {
        if (name == null || name.isBlank()) this.name = this.getCode();
        else this.name = name;
        return (T) this;
    }

    public T setDescription(String description) {
        if (description == null || description.isBlank()) return (T) this;
        this.description = description;
        return (T) this;
    }

    public BaseResponse toBaseResponse() {
        return BaseResponse.of(this.getId(), this.code, this.name);
    }
}
