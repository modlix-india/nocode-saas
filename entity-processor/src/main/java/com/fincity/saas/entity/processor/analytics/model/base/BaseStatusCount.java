package com.fincity.saas.entity.processor.analytics.model.base;

import com.fincity.saas.entity.processor.analytics.model.CountPercentage;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class BaseStatusCount<T extends BaseStatusCount<T>> implements Serializable {

    private CountPercentage totalCount;

    private List<IdAndValue<String, CountPercentage>> perCount;

    public T setTotalCount(CountPercentage totalCount) {
        this.totalCount = totalCount;
        return (T) this;
    }

    public T setPerCount(List<IdAndValue<String, CountPercentage>> perCount) {
        this.perCount = perCount;
        return (T) this;
    }
}
