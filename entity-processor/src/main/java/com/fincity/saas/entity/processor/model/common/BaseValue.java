package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BaseValue implements Serializable, Comparable<BaseValue> {

    @Serial
    private static final long serialVersionUID = 1458279752853060502L;

    private IdAndValue<ULong, String> value;
    private Integer order;

    public static BaseValue of(IdAndValue<ULong, String> value, Integer order) {
        return new BaseValue().setValue(value).setOrder(order);
    }

    public static BaseValue of(ULong id, String name) {
        return of(IdAndValue.of(id, name), null);
    }

    public static BaseValue of(ULong id, String name, Integer order) {
        return of(IdAndValue.of(id, name), order);
    }

    public static Map<ULong, BaseValue> toIdMap(List<BaseValue> baseValueList) {
        return baseValueList.stream().collect(Collectors.toMap(BaseValue::getId, Function.identity(), (a, b) -> b));
    }

    @Override
    public int compareTo(BaseValue other) {
        if (this.order != null && other.order != null) {
            return this.order.compareTo(other.order);
        }
        if (this.order == null && other.order == null) {
            return this.value.getValue().compareTo(other.value.getValue());
        }
        return this.order == null ? 1 : -1;
    }

    @JsonIgnore
    public ULong getId() {
        return this.value.getId();
    }
}
