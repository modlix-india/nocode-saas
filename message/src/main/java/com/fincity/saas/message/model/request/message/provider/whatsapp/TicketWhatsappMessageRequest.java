package com.fincity.saas.message.model.request.message.provider.whatsapp;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class TicketWhatsappMessageRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 1683814048899955813L;

    private Identity ticketId;
    private Message message;
    private FileDetail fileDetail;

    public boolean isValid() {
        return this.ticketId != null && !this.ticketId.isNull() && this.message != null && this.message.getTo() != null;
    }
}
