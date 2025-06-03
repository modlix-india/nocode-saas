package com.fincity.saas.entity.processor.model.base;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ULongEager implements Serializable {

    @Serial
    private static final long serialVersionUID = 1020913068147138769L;

    private ULong id;
    private Map<String, Object> object;

    public ULongEager(ULong id) {
        this.id = id;
    }

    public static ULongEager of(ULong id) {
        return new ULongEager(id);
    }
}
