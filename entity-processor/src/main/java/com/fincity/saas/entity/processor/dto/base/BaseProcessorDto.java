package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.math.BigInteger;
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
public abstract class BaseProcessorDto<T extends BaseProcessorDto<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 5174424228629814984L;

    @Version
    private int version = 1;

    private ULong clientId;

    protected BaseProcessorDto() {
        super();
    }

    protected BaseProcessorDto(BaseProcessorDto<T> baseProcessorDto) {
        super(baseProcessorDto);
        this.version = baseProcessorDto.version;
        this.clientId = baseProcessorDto.clientId;
    }

    public T setClientId(BigInteger clientId) {
        this.clientId = ULong.valueOf(clientId.longValue());
        return (T) this;
    }

    @JsonIgnore
    public ULong getAccessUser() {
        return this.getCreatedBy();
    }

    @JsonIgnore
    public ULong getAccessUserOrCreatedBy() {
        return this.getAccessUser() != null ? this.getAccessUser() : this.getCreatedBy();
    }
}
