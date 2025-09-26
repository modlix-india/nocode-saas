package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Data
@NoArgsConstructor
@FieldNameConstants
public class IdAndValue<I extends Serializable, U extends Serializable>
        implements Comparable<IdAndValue<I, U>>, Serializable {

    public static final String ID_CACHE_KEY = "idAndValue";
    public static final String VALUE_CACHE_KEY = "valueAndId";

    @Serial
    private static final long serialVersionUID = 4741758940431882981L;

    private I id;
    private U value;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean compareId = true;

    public IdAndValue(I id, U value) {
        this.id = id;
        this.value = value;
    }

    public static <I extends Serializable, U extends Serializable> IdAndValue<I, U> of(I id, U value) {
        return new IdAndValue<>(id, value);
    }

    public static <I extends Serializable, U extends Serializable> Map<I, U> toMap(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().collect(Collectors.toMap(IdAndValue::getId, IdAndValue::getValue, (a, b) -> b));
    }

    public static <I extends Serializable, U extends Serializable> Map<U, I> toValueMap(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().collect(Collectors.toMap(IdAndValue::getValue, IdAndValue::getId, (a, b) -> b));
    }

    public static <I extends Serializable, U extends Serializable> List<I> toIdList(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().map(IdAndValue::getId).toList();
    }

    public static <I extends Serializable, U extends Serializable> List<U> toValueList(
            List<IdAndValue<I, U>> idAndValueList) {
        return idAndValueList.stream().map(IdAndValue::getValue).toList();
    }

    public Tuple2<I, U> toTuple() {
        return Tuples.of(id, value);
    }

    @Override
    public int compareTo(IdAndValue<I, U> o) {
        if (o == null) return 1;
        return this.compareId ? compareIds(o) : compareValues(o);
    }

    @SuppressWarnings("unchecked")
    private int compareIds(IdAndValue<I, U> o) {
        if (this.id == null && o.id == null) return 0;
        if (this.id == null) return -1;
        if (o.id == null) return 1;

        if (this.id instanceof Comparable<?> cmpId) return ((Comparable<Object>) cmpId).compareTo(o.id);
        return this.id.toString().compareTo(o.id.toString());
    }

    @SuppressWarnings("unchecked")
    private int compareValues(IdAndValue<I, U> o) {
        if (this.value == null && o.value == null) return 0;
        if (this.value == null) return -1;
        if (o.value == null) return 1;

        if (this.value instanceof Comparable<?> cmpVal) return ((Comparable<Object>) cmpVal).compareTo(o.value);
        return this.value.toString().compareTo(o.value.toString());
    }
}
