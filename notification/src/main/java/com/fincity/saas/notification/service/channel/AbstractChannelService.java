package com.fincity.saas.notification.service.channel;

import com.fincity.saas.notification.enums.channel.ChannelType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.oserver.core.document.Connection;
import com.fincity.saas.notification.service.NotificationMessageResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class AbstractChannelService implements ChannelType {

    protected NotificationMessageResourceService msgService;

    protected Logger logger;

    protected AbstractChannelService() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    protected abstract <T> Mono<T> throwSendError(Object... params);

    @Autowired
    private void setMessageResourceService(NotificationMessageResourceService messageResourceService) {
        this.msgService = messageResourceService;
    }

    protected Mono<Boolean> hasValidConnection(Connection connection) {

        if (connection == null) return this.throwSendError("Connection details are missing");

        NotificationChannelType connectionChannelType =
                NotificationChannelType.getFromConnectionSubType(connection.getConnectionSubType());

        if (connectionChannelType == null || !connectionChannelType.equals(this.getChannelType()))
            return this.throwSendError("Connection details are missing");

        return Mono.just(Boolean.TRUE);
    }
}
