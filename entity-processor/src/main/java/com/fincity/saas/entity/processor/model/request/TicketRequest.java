package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketRequest extends BaseRequest<TicketRequest> {

    @Serial
    private static final long serialVersionUID = 3948634318723751023L;

    private Identity productId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String source;
    private String subSource;
}
