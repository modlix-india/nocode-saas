package com.fincity.saas.commons.core.document.common.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.common.notification.NotificationRecipientType;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.scheduling.support.CronExpression;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1, 'name': 1, }", name = "notificationFilteringIndex", unique = true)
@CompoundIndex(
        def = "{'appCode': 1, 'clientCode': 1, 'notificationType': 1, }",
        name = "notificationFilteringIndex",
        unique = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Notification extends AbstractOverridableDTO<Notification> {

    @Serial
    private static final long serialVersionUID = 4924671644117461908L;

    private String notificationType;
    private String connectionName;
    private Map<String, NotificationTemplate> channelDetails;

    public Notification(Notification notification) {
        super(notification);
        this.notificationType = notification.notificationType;
        this.connectionName = notification.connectionName;
        this.channelDetails = CloneUtil.cloneMapObject(notification.channelDetails);
    }

    public void updateChannelDetails(Map<String, NotificationTemplate> channelDetails) {

        this.getChannelDetails().entrySet().stream()
                .filter(entry -> channelDetails.containsKey(entry.getKey()))
                .forEach(entry -> channelDetails.put(
                        entry.getKey(),
                        channelDetails
                                .get(entry.getKey())
                                .setCode(entry.getValue().getCode())));

        this.setChannelDetails(channelDetails);
    }

    public Map<String, String> getChannelTemplateCodes() {
        return this.getChannelDetails().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getCode(), (a, b) -> b));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Notification> applyOverride(Notification base) {
        if (base == null) return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> DifferenceApplicator.apply(this.channelDetails, base.channelDetails), ch -> {
                            this.channelDetails = (Map<String, NotificationTemplate>) ch;

                            if (this.notificationType == null) this.notificationType = base.notificationType;
                            if (this.connectionName == null) this.connectionName = base.connectionName;

                            return Mono.just(this);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Notification.applyOverride"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Notification> makeOverride(Notification base) {
        if (base == null) return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> Mono.just(this),
                        obj -> DifferenceExtractor.extract(obj.channelDetails, base.channelDetails),
                        (obj, ch) -> {
                            obj.channelDetails = (Map<String, NotificationTemplate>) ch;

                            if (obj.notificationType == null) obj.notificationType = base.notificationType;
                            if (obj.connectionName == null) obj.connectionName = base.connectionName;
                            return Mono.just(obj);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Notification.makeOverride"));
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class NotificationTemplate implements Serializable, IDifferentiable<NotificationTemplate> {

        @Serial
        private static final long serialVersionUID = 1054865111921742820L;

        private String code = UniqueUtil.shortUUID();
        private Map<String, Map<String, String>> templateParts;
        private Map<String, Object> variableSchema; // NOSONAR
        private Map<String, String> resources;
        private String defaultLanguage;
        private String languageExpression;
        private Map<String, String> recipientExpressions;
        private DeliveryOptions deliveryOptions;

        public NotificationTemplate(NotificationTemplate template) {
            this.code = template.code;
            this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
            this.variableSchema = CloneUtil.cloneMapObject(template.variableSchema);
            this.resources = CloneUtil.cloneMapObject(template.resources);
            this.defaultLanguage = template.defaultLanguage;
            this.languageExpression = template.languageExpression;
            this.recipientExpressions = CloneUtil.cloneMapObject(template.recipientExpressions);
            this.deliveryOptions = template.deliveryOptions;
        }

        @JsonIgnore
        public boolean isValidForEmail() {
            if (this.recipientExpressions == null || this.recipientExpressions.isEmpty()) return false;

            return this.recipientExpressions.containsKey(NotificationRecipientType.FROM.getLiteral())
                    && this.recipientExpressions.containsKey(NotificationRecipientType.TO.getLiteral());
        }

        @Override
        public Mono<NotificationTemplate> extractDifference(NotificationTemplate inc) { // NOSONAR
            if (inc == null) return Mono.just(new NotificationTemplate());

            return FlatMapUtil.flatMapMonoWithNull(
                    () -> DifferenceExtractor.extract(inc.deliveryOptions, this.deliveryOptions), de -> {
                        NotificationTemplate diff = new NotificationTemplate();

                        if (!this.code.equals(inc.code)) diff.setCode(this.code);

                        if (!this.templateParts.equals(inc.templateParts))
                            diff.setTemplateParts(CloneUtil.cloneMapObject(this.templateParts));

                        if (!this.variableSchema.equals(inc.variableSchema))
                            diff.setVariableSchema(CloneUtil.cloneMapObject(this.variableSchema));

                        if (!this.resources.equals(inc.resources))
                            diff.setResources(CloneUtil.cloneMapObject(this.resources));

                        if (!this.defaultLanguage.equals(inc.defaultLanguage))
                            diff.setDefaultLanguage(this.defaultLanguage);

                        if (!this.languageExpression.equals(inc.languageExpression))
                            diff.setLanguageExpression(this.languageExpression);

                        if (!this.recipientExpressions.equals(inc.recipientExpressions))
                            diff.setRecipientExpressions(CloneUtil.cloneMapObject(this.recipientExpressions));

                        if (de != null) diff.setDeliveryOptions(this.deliveryOptions);
                        return Mono.just(diff);
                    });
        }

        @Override
        public Mono<NotificationTemplate> applyOverride(NotificationTemplate override) { // NOSONAR
            if (override == null) return Mono.just(new NotificationTemplate());

            return FlatMapUtil.flatMapMonoWithNull(
                    () -> Mono.just(this),
                    obj -> DifferenceApplicator.apply(override.deliveryOptions, this.deliveryOptions),
                    (obj, de) -> {
                        if (de != null) obj.setDeliveryOptions((DeliveryOptions) de);
                        if (override.code != null) obj.code = override.code;
                        if (override.templateParts != null)
                            obj.templateParts = CloneUtil.cloneMapObject(override.templateParts);
                        if (override.variableSchema != null)
                            obj.variableSchema = CloneUtil.cloneMapObject(override.variableSchema);
                        if (override.resources != null) obj.resources = CloneUtil.cloneMapObject(override.resources);
                        if (override.defaultLanguage != null) obj.defaultLanguage = override.defaultLanguage;
                        if (override.languageExpression != null) obj.languageExpression = override.languageExpression;
                        if (override.recipientExpressions != null)
                            obj.recipientExpressions = CloneUtil.cloneMapObject(override.recipientExpressions);

                        return Mono.just(obj);
                    });
        }
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class DeliveryOptions implements Serializable, IDifferentiable<DeliveryOptions> {

        @Serial
        private static final long serialVersionUID = 3684669126118045688L;

        private boolean instant = Boolean.TRUE;
        private String cronStatement;
        private boolean allowUnsubscribing = Boolean.TRUE;

        public DeliveryOptions(DeliveryOptions deliveryOptions) {
            this.instant = deliveryOptions.instant;
            this.cronStatement = deliveryOptions.cronStatement;
            this.allowUnsubscribing = deliveryOptions.allowUnsubscribing;
        }

        public boolean isValid() {
            return this.instant ? Boolean.TRUE : CronExpression.isValidExpression(this.cronStatement);
        }

        @Override
        public Mono<DeliveryOptions> extractDifference(DeliveryOptions inc) {

            DeliveryOptions deliveryOptions = new DeliveryOptions();

            if (inc.instant != this.instant) deliveryOptions.instant = this.instant;
            if (inc.cronStatement == null || !inc.cronStatement.equals(this.cronStatement))
                deliveryOptions.cronStatement = this.cronStatement;
            if (inc.allowUnsubscribing != this.allowUnsubscribing)
                deliveryOptions.allowUnsubscribing = this.allowUnsubscribing;

            return Mono.just(deliveryOptions);
        }

        @Override
        public Mono<DeliveryOptions> applyOverride(DeliveryOptions override) {
            if (override == null) return Mono.just(this);

            if (override.instant != this.instant) this.instant = override.instant;

            if (override.cronStatement != null) this.cronStatement = override.cronStatement;

            if (override.allowUnsubscribing != this.allowUnsubscribing)
                this.allowUnsubscribing = override.allowUnsubscribing;

            return Mono.just(this);
        }
    }
}
