package com.fincity.saas.entity.processor.model.common;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.util.CloneUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
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

    public static Schema getSchema() {
        return Schema.ofObject(ValueContainer.class.getSimpleName())
                .setProperties(Map.of(
                        Fields.value, Schema.ofAny(Fields.value),
                        Fields.toValue, Schema.ofAny(Fields.toValue),
                        Fields.multiValue, Schema.ofArray(Fields.multiValue, Schema.ofAny(Fields.multiValue))));
    }
}
