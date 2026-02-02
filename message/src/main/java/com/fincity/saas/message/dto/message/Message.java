package com.fincity.saas.message.dto.message;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.util.NameUtil;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

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
    private ULong whatsappMessageId;

    public Message() {
        super();
        this.relationsMap.put(Fields.whatsappMessageId, MessageSeries.WHATSAPP_MESSAGE.getTable());
    }

    public Message(Message message) {
        super(message);
        this.connectionName = message.connectionName;
        this.messageProvider = message.messageProvider;
        this.isOutbound = message.isOutbound;
        this.whatsappMessageId = message.whatsappMessageId;
    }

    public Message setMessageProvider(String messageProvider) {
        this.messageProvider = NameUtil.normalizeToUpper(messageProvider);
        return this;
    }
}
