package com.fincity.saas.message.model.common;

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
public class IdAndValue<I extends Serializable, U extends Serializable> implements Serializable {

    public static final String ID_CACHE_KEY = "idAndValue";
    public static final String VALUE_CACHE_KEY = "valueAndId";

    @Serial
    private static final long serialVersionUID = 4741758940431882981L;

    private I id;
    private U value;

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
}
