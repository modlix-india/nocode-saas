package com.fincity.saas.notification.oserver.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.notification.oserver.core.document.Notification;
import com.fincity.saas.notification.service.template.NotificationTemplateProcessor;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import jakarta.annotation.PostConstruct;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class NotificationService extends AbstractCoreService<Notification> {

    private static final Pattern NOTI_PUB_MESSAGE_PATTERN =
            Pattern.compile("Notification\\s*:\\s*(.*?),\\s*AppCode\\s*:\\s*(.*?),\\s*ClientCode\\s*:\\s*(.*)");

    private final NotificationTemplateProcessor notificationTemplateProcessor;

    @Autowired(required = false) // NOSONAR
    @Qualifier("subRedisAsyncCommand") private RedisPubSubAsyncCommands<String, String> subAsyncCommand;

    @Autowired(required = false) // NOSONAR
    private StatefulRedisPubSubConnection<String, String> subConnect;

    @Value("${redis.notification.eviction.channel:notificationChannel}")
    private String channel;

    public NotificationService(NotificationTemplateProcessor notificationTemplateProcessor) {
        this.notificationTemplateProcessor = notificationTemplateProcessor;
    }

    @PostConstruct
    public void init() {
        if (subAsyncCommand == null || subConnect == null) return;

        subAsyncCommand.subscribe(channel);
        subConnect.addListener(new TemplateCacheEvictionListener());
    }

    @Override
    protected Mono<Notification> fetchCoreDocument(
            String appCode, String urlClientCode, String clientCode, String documentName) {
        return super.coreService.getNotification(urlClientCode, documentName, appCode, clientCode);
    }

    public Mono<Boolean> evictChannelEntities(String appCode, String clientCode, String notificationName) {
        return FlatMapUtil.flatMapMono(
                () -> super.getCoreDocument(appCode, clientCode, clientCode, notificationName),
                notification ->
                        notificationTemplateProcessor.evictTemplateCache(notification.getChannelTemplateCodeMap()));
    }

    private class TemplateCacheEvictionListener extends RedisPubSubAdapter<String, String> {

        @Override
        public void message(String channel, String message) {
            if (NotificationService.this.channel.equals(channel)) {
                processTemplateEviction(message);
            }
        }

        private void processTemplateEviction(String pubMessage) {
            this.processNotificationPubMessage(pubMessage)
                    .flatMap(notificationInfo -> NotificationService.this.evictChannelEntities(
                            notificationInfo.getT1(), notificationInfo.getT2(), notificationInfo.getT3()))
                    .subscribe();
        }

        private Mono<Tuple3<String, String, String>> processNotificationPubMessage(String message) {
            Matcher matcher = NOTI_PUB_MESSAGE_PATTERN.matcher(message);
            if (!matcher.matches()) return Mono.empty();
            return Mono.just(Tuples.of(
                    matcher.group(2).trim(),
                    matcher.group(3).trim(),
                    matcher.group(1).trim()));
        }
    }
}
