package com.fincity.saas.entity.processor.model.base;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BaseResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1845110916281936020L;

    private ULong id;
    private String code;
    private String name;

    public static BaseResponse of(ULong id, String code, String name) {
        return new BaseResponse().setId(id).setCode(code).setName(name);
    }
}
