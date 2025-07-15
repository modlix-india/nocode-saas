package com.fincity.saas.message.dto.base;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import java.io.Serial;
import java.util.Collection;
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
public abstract class BaseUpdatableDto<T extends BaseUpdatableDto<T>> extends AbstractUpdatableDTO<ULong, ULong> {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 7711286953339453235L;

    private String appCode;
    private String clientCode;

    private String code = UniqueUtil.shortUUID();

    private boolean isActive = Boolean.TRUE;

    protected BaseUpdatableDto() {
        super();
    }

    protected BaseUpdatableDto(BaseUpdatableDto<T> baseUpdatableDto) {

        super();

        this.setId(baseUpdatableDto.getId());
        this.setCreatedAt(baseUpdatableDto.getCreatedAt());
        this.setCreatedBy(baseUpdatableDto.getCreatedBy());
        this.setUpdatedAt(baseUpdatableDto.getUpdatedAt());
        this.setUpdatedBy(baseUpdatableDto.getUpdatedBy());

        this.setAppCode(baseUpdatableDto.getAppCode());
        this.setClientCode(baseUpdatableDto.getClientCode());

        this.code = baseUpdatableDto.code;
        this.isActive = baseUpdatableDto.isActive;
    }

    public static <T extends BaseUpdatableDto<T>> Map<ULong, T> toIdMap(Collection<T> baseDtoList) {
        return baseDtoList.stream()
                .collect(Collectors.toMap(BaseUpdatableDto::getId, Function.identity(), (a, b) -> b));
    }

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }
}
