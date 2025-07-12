package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.commons.util.CloneUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ValueContainer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1608132880815821600L;

    private Object value;
    private Object toValue;
    private List<?> multiValue;

    public ValueContainer() {}

    public ValueContainer(ValueContainer valueContainer) {
        this.value = valueContainer.value;
        this.toValue = valueContainer.toValue;
        this.multiValue = CloneUtil.cloneMapList(valueContainer.multiValue);
    }
}
