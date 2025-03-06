package com.fincity.saas.core.document;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.scheduling.support.CronExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.enums.notification.NotificationRecipientType;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.google.gson.JsonObject;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1, 'name': 1, }", name = "notificationFilteringIndex", unique = true)
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1, 'notificationType': 1, }", name = "notificationFilteringIndex", unique = true)
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

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Notification> applyOverride(Notification base) {
		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMonoWithNull(

						() -> DifferenceApplicator.apply(this.channelConnections, base.channelConnections),

						cc -> DifferenceApplicator.apply(this.channelDetails, base.channelDetails),

						(cc, ch) -> {
							this.channelConnections = (Map<String, String>) cc;

							this.channelDetails = (Map<String, NotificationTemplate>) ch;

							if (this.notificationType == null)
								this.notificationType = base.notificationType;

							return Mono.just(this);
						})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "Notification.applyOverride"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Notification> makeOverride(Notification base) {
		if (base == null)
			return Mono.just(this);

		return FlatMapUtil.flatMapMonoWithNull(
						() -> Mono.just(this),

						obj -> DifferenceExtractor.extract(obj.channelConnections, base.channelConnections),

						(obj, cc) -> DifferenceExtractor.extract(obj.channelDetails, base.channelDetails),

						(obj, cc, ch) -> {
							obj.channelConnections = (Map<String, String>) cc;

							obj.channelDetails = (Map<String, NotificationTemplate>) ch;

							if (obj.notificationType == null)
								obj.notificationType = base.notificationType;

							return Mono.just(obj);
						})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "Notification.makeOverride"));
	}

	@Data
	@Accessors(chain = true)
	@NoArgsConstructor
	public static class NotificationTemplate implements Serializable {

		@JsonIgnore
		private String code = UniqueUtil.shortUUID();

		private Map<String, Map<String, String>> templateParts;
		private JsonObject variableSchema; // NOSONAR
		private Map<String, String> resources;
		private String defaultLanguage;
		private String languageExpression;
		private Map<String, String> recipientExpressions;
		private DeliveryOptions deliveryOptions;

		public NotificationTemplate(NotificationTemplate template) {
			this.code = template.code;
			this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
			this.variableSchema = template.variableSchema.deepCopy();
			this.resources = CloneUtil.cloneMapObject(template.resources);
			this.defaultLanguage = template.defaultLanguage;
			this.languageExpression = template.languageExpression;
			this.recipientExpressions = CloneUtil.cloneMapObject(template.recipientExpressions);
			this.deliveryOptions = template.deliveryOptions;
		}

		public boolean isValidSchema() {

			if (this.variableSchema == null)
				return true;

			return !this.variableSchema.isJsonNull() && !this.variableSchema.isJsonObject();
		}

		public boolean isValidForEmail() {
			if (this.recipientExpressions == null || this.recipientExpressions.isEmpty())
				return false;

			return this.recipientExpressions.containsKey(NotificationRecipientType.FROM.getLiteral()) &&
					this.recipientExpressions.containsKey(NotificationRecipientType.TO.getLiteral());
		}
	}

	@Data
	@Accessors(chain = true)
	@NoArgsConstructor
	public static class DeliveryOptions implements Serializable {

		private boolean instant = Boolean.TRUE;
		private String cronStatement;
		private boolean allowUnsubscribing = Boolean.TRUE;

		public DeliveryOptions(DeliveryOptions deliveryOptions) {
			this.instant = deliveryOptions.instant;
			this.cronStatement = deliveryOptions.cronStatement;
			this.allowUnsubscribing = deliveryOptions.allowUnsubscribing;
		}

		public boolean isValid() {
			return CronExpression.isValidExpression(this.cronStatement);
		}

	}
}
