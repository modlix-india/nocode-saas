package com.fincity.saas.message.model.request.message.provider.whatsapp;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
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
public class WhatsappMessageCswRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 1500688362601095963L;

    private Identity whatsappPhoneNumberId;

    private PhoneNumber customerNumber;

    public static WhatsappMessageCswRequest of(
            String connectionName, Identity whatsappPhoneNumberId, PhoneNumber customerNumber) {
        return (WhatsappMessageCswRequest) new WhatsappMessageCswRequest()
                .setWhatsappPhoneNumberId(whatsappPhoneNumberId)
                .setCustomerNumber(customerNumber)
                .setConnectionName(connectionName);
    }
}
