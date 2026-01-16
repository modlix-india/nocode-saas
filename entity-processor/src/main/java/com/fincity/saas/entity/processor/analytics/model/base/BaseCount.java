package com.fincity.saas.entity.processor.analytics.model.base;

import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class BaseCount<T extends BaseCount<T>> implements Serializable, IClassConvertor {

    private CountPercentage totalCount;

    public T setTotalCount(CountPercentage totalCount) {
        this.totalCount = totalCount;
        return (T) this;
    }
}
