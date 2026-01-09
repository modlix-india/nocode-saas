package com.fincity.saas.entity.processor.model.request;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PartnerManagerUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6293847102938475610L;

    private ULong managerId;
}
