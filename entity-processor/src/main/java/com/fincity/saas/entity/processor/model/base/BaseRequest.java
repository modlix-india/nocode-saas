package com.fincity.saas.entity.processor.model.base;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public abstract class BaseRequest<T extends BaseRequest<T>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1728772398532984982L;

    private String name;
    private String description;

    public T setName(String name) {
        this.name = name.trim();
        return (T) this;
    }

    public T setDescription(String description) {
        this.description = description.trim();
        return (T) this;
    }
}
