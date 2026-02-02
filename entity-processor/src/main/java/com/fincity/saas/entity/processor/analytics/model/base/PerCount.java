package com.fincity.saas.entity.processor.analytics.model.base;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class PerCount<T extends PerCount<T>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 4350179029488896165L;

    private ULong groupedId;
    private String groupedValue;
    private String mapValue;
    private Long count;

    public T create(ULong groupedId, String mapValue, Long count) {
        return new PerCount<T>() {}.setGroupedId(groupedId)
                .setMapValue(mapValue)
                .setCount(count);
    }

    public T create(String groupedValue, String mapValue, Long count) {
        return new PerCount<T>() {}.setGroupedValue(groupedValue)
                .setMapValue(mapValue)
                .setCount(count);
    }

    public T setGroupedId(ULong groupedId) {
        this.groupedId = groupedId;
        return (T) this;
    }

    public T setMapValue(String mapValue) {
        this.mapValue = mapValue;
        return (T) this;
    }

    public T setGroupedValue(String groupedValue) {
        this.groupedValue = groupedValue;
        return (T) this;
    }

    public T setCount(Long count) {
        this.count = count;
        return (T) this;
    }
}
