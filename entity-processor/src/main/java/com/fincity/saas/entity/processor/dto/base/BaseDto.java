package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.util.IClassConvertor;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public class BaseDto<T extends BaseDto<T>> extends AbstractFlowUpdatableDTO<ULong, ULong> implements IClassConvertor {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 1844345864104376760L;

    private String code = UniqueUtil.shortUUID();

    private String name = this.code;
    private String description;

    @JsonIgnore
    private boolean tempActive = Boolean.FALSE;

    private boolean isActive = Boolean.TRUE;

    public static <T extends BaseDto<T>> Map<ULong, T> toIdMap(List<T> baseDtoList) {
        return baseDtoList.stream().collect(Collectors.toMap(BaseDto::getId, Function.identity(), (a, b) -> b));
    }

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }

    public T setName(String name) {
        if (name == null || name.isBlank()) this.name = this.getCode();
        else this.name = NameUtil.normalize(name);
        return (T) this;
    }

    public T setDescription(String description) {
        if (description == null || description.isBlank()) return (T) this;
        this.description = NameUtil.normalize(description);
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
