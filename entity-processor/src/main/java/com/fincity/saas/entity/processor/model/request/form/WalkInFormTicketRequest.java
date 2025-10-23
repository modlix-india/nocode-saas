package com.fincity.saas.entity.processor.model.request.form;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
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
public class WalkInFormTicketRequest extends BaseRequest<WalkInFormTicketRequest> {

    @Serial
    private static final long serialVersionUID = 5455857302595973770L;

    private ULong userId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String subSource;
    private String comment;
}
