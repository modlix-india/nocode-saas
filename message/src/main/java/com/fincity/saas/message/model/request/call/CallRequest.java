package com.fincity.saas.message.model.request.call;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.PhoneNumber;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CallRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 6013191012022643981L;

    private PhoneNumber toNumber;
    private PhoneNumber callerId;
}
