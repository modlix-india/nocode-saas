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
public abstract class BaseProductTemplate<T extends BaseProductTemplate<T>> extends BaseRequest<T> {

    private Identity productTemplateId;
    private Map<Integer, T> children;

    public T setProductTemplateId(Identity productTemplateId) {
        this.productTemplateId = productTemplateId;
        return (T) this;
    }

    public boolean areChildrenValid() {
        return Boolean.TRUE;
    }
}
