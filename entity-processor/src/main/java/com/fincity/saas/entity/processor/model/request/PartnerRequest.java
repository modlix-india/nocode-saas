package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PartnerRequest extends BaseRequest<PartnerRequest> {

    @Serial
    private static final long serialVersionUID = 8432447203359141912L;

    private ULong clientId;
}
