package com.fincity.saas.notification.configuration;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import java.util.stream.Stream;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MqNameProvider {

    @Value("${events.mq.queue.prefix:notification.queue}")
    private String queueNamePrefix;

    public String[] getEmailQueues() {
        return new String[] {NotificationChannelType.EMAIL.getMqQueueName(getQueueNamePrefix())};
    }

    public String[] getInAppQueues() {
        return new String[] {NotificationChannelType.IN_APP.getMqQueueName(getQueueNamePrefix())};
    }

    public String[] getAllQueues() {
        return Stream.of(getEmailQueues(), getInAppQueues()).flatMap(Stream::of).toArray(String[]::new);
    }
}
