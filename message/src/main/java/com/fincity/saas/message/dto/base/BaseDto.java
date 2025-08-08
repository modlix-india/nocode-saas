package com.fincity.saas.message.dto.base;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.UniqueUtil;
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
public abstract class BaseDto<T extends BaseDto<T>> extends AbstractDTO<ULong, ULong> {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 5616523481646995677L;

    private String appCode;
    private String clientCode;

    private String code = UniqueUtil.shortUUID();

    private boolean isActive = Boolean.TRUE;

    protected BaseDto() {
        super();
    }

    protected BaseDto(BaseDto<T> baseDto) {
        super();
        this.setId(baseDto.getId());
        this.setCreatedAt(baseDto.getCreatedAt());
        this.setCreatedBy(baseDto.getCreatedBy());

        this.setAppCode(baseDto.getAppCode());
        this.setClientCode(baseDto.getClientCode());

        this.code = baseDto.code;
        this.isActive = baseDto.isActive;
    }

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }
}
