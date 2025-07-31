package com.fincity.saas.message.dto.message;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageStatus;
import com.fincity.saas.message.util.NameUtil;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.util.Map;
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

    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;
    private String connectionName;
    private String messageProvider;
    private Boolean isOutbound;
    private MessageStatus messageStatus;
    private String sentTime;
    private String deliveredTime;
    private String readTime;
    private ULong whatsappMessageId;
    private ULong whatsappTemplateId;
    private Map<String, Object> metadata;

    public Message setMessageProvider(String messageProvider) {
        this.messageProvider = NameUtil.normalizeToUpper(messageProvider);
        return this;
    }
}
