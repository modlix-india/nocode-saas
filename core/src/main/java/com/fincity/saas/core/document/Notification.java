package com.fincity.saas.core.document;

import java.io.Serial;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.core.model.NotificationTemplate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1, 'name': 1, }", name = "notificationFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Notification extends AbstractOverridableDTO<Template> {

	@Serial
	private static final long serialVersionUID = 4924671644117461908L;

	private String notificationType;
	private Map<String, NotificationTemplate> channelDetails;

	@Override
	public Mono<Template> applyOverride(Template base) {
		return null;
	}

	@Override
	public Mono<Template> makeOverride(Template base) {
		return null;
	}
}
