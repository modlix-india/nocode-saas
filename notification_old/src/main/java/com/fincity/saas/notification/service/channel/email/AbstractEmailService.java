package com.fincity.saas.notification.service.channel.email;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.exception.NotificationDeliveryException;
import com.fincity.saas.notification.service.NotificationMessageResourceService;
import com.fincity.saas.notification.service.channel.AbstractChannelService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class AbstractEmailService extends AbstractChannelService {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    protected <T> Mono<T> throwSendError(Object... params) {
        return msgService.throwMessage(
                msg -> new NotificationDeliveryException(this.getChannelType(), msg),
                NotificationMessageResourceService.MAIL_SEND_ERROR,
                params);
    }

    protected String generateContentId(String fromAddress) {

        if (fromAddress == null) return null;

        String domain = fromAddress.contains("@") ? fromAddress.split("@")[1] : "modlix.com";

        return System.currentTimeMillis() + "." + UniqueUtil.shortUUID() + "@" + domain;
    }
}
