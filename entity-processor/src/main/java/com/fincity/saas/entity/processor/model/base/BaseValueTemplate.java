package com.fincity.saas.entity.processor.model.base;

import com.fincity.saas.entity.processor.model.common.Identity;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class BaseValueTemplate<T extends BaseValueTemplate<T>> extends BaseRequest<T> {

    private Identity valueTemplateId;
    private Map<Integer, T> children;

    public T setValueTemplateId(Identity valueTemplateId) {
        this.valueTemplateId = valueTemplateId;
        return (T) this;
    }
}
