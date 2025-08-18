package com.fincity.saas.message.model.request.message;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.PhoneNumber;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class MessageRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 1683814048899955806L;

    private PhoneNumber toNumber;
    private String text;

    public boolean isValid() {
        return (this.toNumber != null && this.toNumber.getNumber() != null) || this.text != null;
    }
}
