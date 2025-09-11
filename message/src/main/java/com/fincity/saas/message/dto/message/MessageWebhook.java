package com.fincity.saas.message.dto.message;

import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.util.NameUtil;
import java.io.Serial;
import java.util.Map;
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
public class MessageWebhook extends BaseUpdatableDto<MessageWebhook> {

    @Serial
    private static final long serialVersionUID = 7803512026491327812L;

    private String provider;
    private boolean isProcessed = Boolean.FALSE;
    private Map<String, Object> event;

    public MessageWebhook() {
        super();
    }

    public MessageWebhook(MessageWebhook messageWebhook) {
        super(messageWebhook);
        this.provider = messageWebhook.provider;
        this.isProcessed = messageWebhook.isProcessed;
        this.event = CloneUtil.cloneMapObject(messageWebhook.event);
    }

    public MessageWebhook setProvider(String provider) {
        this.provider = NameUtil.normalizeToUpper(provider);
        return this;
    }
}
