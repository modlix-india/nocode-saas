package com.fincity.saas.notification.document.common.core;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.template.NotificationTemplate;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Notification extends AbstractOverridableDTO<Notification> {

    @Serial
    private static final long serialVersionUID = 4924671644117461908L;

    private String notificationType;
    private Map<String, String> channelConnections;
    private Map<String, NotificationTemplate> channelDetails;

    public Notification(Notification notification) {
        super(notification);
        this.notificationType = notification.notificationType;
        this.channelConnections = CloneUtil.cloneMapObject(notification.channelConnections);
        this.channelDetails = CloneUtil.cloneMapObject(notification.channelDetails);
    }

    @Override
    public Mono<Notification> applyOverride(Notification base) {
        return Mono.just(this);
    }

    @Override
    public Mono<Notification> makeOverride(Notification base) {
        return Mono.just(this);
    }

    public Map<NotificationChannelType, NotificationTemplate> getChannelDetailMap() {
        return NotificationChannelType.getChannelTypeMap(this.channelDetails);
    }

    public Map<NotificationChannelType, String> getChannelConnectionMap() {
        return NotificationChannelType.getChannelTypeMap(this.channelConnections);
    }
}
