package com.fincity.saas.message.dto.message;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.util.NameUtil;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Message extends BaseUpdatableDto<Message> {

    @Serial
    private static final long serialVersionUID = 2564137597863545676L;

    private String connectionName;
    private String messageProvider;
    private Boolean isOutbound;

    // whatsappMessageId, and its relation to the WHATSAPP_MESSAGE series, retired with the Cloud
    // API. WhatsApp conversation content lives in entity-processor and is keyed by WhatsApp's own
    // message id, so a foreign key from here to a table this service no longer writes would only
    // ever have pointed at history that has already moved.

    public Message() {
        super();
    }

    public Message(Message message) {
        super(message);
        this.connectionName = message.connectionName;
        this.messageProvider = message.messageProvider;
        this.isOutbound = message.isOutbound;
    }

    public Message setMessageProvider(String messageProvider) {
        this.messageProvider = NameUtil.normalizeToUpper(messageProvider);
        return this;
    }
}
