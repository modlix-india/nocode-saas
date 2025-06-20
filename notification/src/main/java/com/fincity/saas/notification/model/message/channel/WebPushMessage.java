package com.fincity.saas.notification.model.message.channel;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class WebPushMessage extends NotificationMessage<WebPushMessage> {

    @Serial
    private static final long serialVersionUID = 1093811688566066278L;

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.WEB_PUSH;
    }
}
