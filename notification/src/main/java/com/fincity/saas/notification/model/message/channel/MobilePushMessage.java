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
public class MobilePushMessage extends NotificationMessage<MobilePushMessage> {

    @Serial
    private static final long serialVersionUID = 1724870485760790646L;

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.MOBILE_PUSH;
    }
}
