package com.modlix.saas.notification.service;

import com.modlix.saas.commons2.mq.notifications.NotificationQueObject;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationsQueListener {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationsQueListener.class);

    private final NotificationSendService notificationSendService;

    public NotificationsQueListener(NotificationSendService notificationSendService) {
        this.notificationSendService = notificationSendService;
    }

    @RabbitListener(queues = "#{'${notifications.mq.queues:notifications1,notifications2,notifications3}'.split(',')}", containerFactory = "directMessageListener", messageConverter = "jsonMessageConverter")
    public void receive(@Payload NotificationQueObject qob, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        if (qob.getXDebug() != null) {
            MDC.put(LogUtil.DEBUG_KEY, qob.getXDebug());
        }

        logger.info("{} - Received notification message: {}", qob.getXDebug(), qob);

        try {
            this.notificationSendService.sendNotification(qob);
            logger.info("{} - Sent notification message: {}", qob.getXDebug(), qob);
        } catch (Exception ex) {
            logger.error("Failed to send notification : {}", qob, ex);
        } finally {

            if (qob.getXDebug() != null) {
                MDC.remove(LogUtil.DEBUG_KEY);
            }
        }
    }
}
