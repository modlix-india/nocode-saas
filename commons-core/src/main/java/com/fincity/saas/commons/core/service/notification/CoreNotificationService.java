package com.fincity.saas.commons.core.service.notification;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Notification;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CoreNotificationService extends AbstractOverridableDataService<Notification, NotificationRepository> {

    protected CoreNotificationService() {
        super(Notification.class);
    }

    @Override
    protected Mono<Notification> updatableEntity(Notification entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setNotificationType(entity.getNotificationType());
                    existing.setDefaultLanguage(entity.getDefaultLanguage());
                    existing.setLanguageExpression(entity.getLanguageExpression());
                    existing.setVariableSchema(entity.getVariableSchema());
                    existing.setChannelTemplates(entity.getChannelTemplates());
                    existing.setChannelConnections(entity.getChannelConnections());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
    }
}