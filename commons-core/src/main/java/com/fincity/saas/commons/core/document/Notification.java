package com.fincity.saas.commons.core.document;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

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
    private String defaultLanguage;
    private String languageExpression;

    private Map<String, Object> variableSchema; // NOSONAR

    private Map<String, NotificationTemplate> channelTemplates;
    private Map<String, String> channelConnections;

    // This is used in cloning using reflection.
    @SuppressWarnings("unused")
    public Notification(Notification notification) {
        super(notification);
        this.notificationType = notification.notificationType;
        this.variableSchema = notification.variableSchema;
        this.defaultLanguage = notification.defaultLanguage;
        this.languageExpression = notification.languageExpression;

        this.channelTemplates = CloneUtil.cloneMapObject(notification.channelTemplates);
        this.channelConnections = CloneUtil.cloneMapObject(notification.channelConnections);
        this.variableSchema = CloneUtil.cloneMapObject(notification.variableSchema);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Notification> applyOverride(Notification base) {
        if (base == null)
            return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> DifferenceApplicator.apply(this.channelTemplates, base.channelTemplates),
                        ch -> DifferenceApplicator.apply(this.channelConnections, base.channelConnections),
                        (ch, conn) -> DifferenceApplicator.apply(this.variableSchema, base.variableSchema),
                        (ch, conn, varSchema) -> {
                            this.channelTemplates = (Map<String, NotificationTemplate>) ch;
                            this.channelConnections = (Map<String, String>) conn;
                            this.variableSchema = (Map<String, Object>) varSchema;

                            if (this.notificationType == null)
                                this.notificationType = base.notificationType;
                            if (this.defaultLanguage == null)
                                this.defaultLanguage = base.defaultLanguage;
                            if (this.languageExpression == null)
                                this.languageExpression = base.languageExpression;

                            return Mono.just(this);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Notification.applyOverride"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Notification> extractDifference(Notification base) {
        if (base == null)
            return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> Mono.just(this),
                        obj -> DifferenceExtractor.extract(obj.channelTemplates, base.channelTemplates),
                        (obj, ch) -> DifferenceExtractor.extract(obj.channelConnections, base.channelConnections),
                        (obj, ch, conn) -> DifferenceExtractor.extract(obj.variableSchema, base.variableSchema),
                        (obj, ch, conn, varSchema) -> {
                            obj.channelTemplates = (Map<String, NotificationTemplate>) ch;
                            obj.channelConnections = (Map<String, String>) conn;
                            obj.variableSchema = (Map<String, Object>) varSchema;

                            if (obj.notificationType == null)
                                obj.notificationType = base.notificationType;
                            if (obj.defaultLanguage == null)
                                obj.defaultLanguage = base.defaultLanguage;
                            if (obj.languageExpression == null)
                                obj.languageExpression = base.languageExpression;
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

        private Map<String, Map<String, String>> templateParts;
        private Map<String, String> resources;

        // Used using reflection in cloning utils.
        @SuppressWarnings("unused")
        public NotificationTemplate(NotificationTemplate template) {

            this.templateParts = CloneUtil.cloneMapObject(template.templateParts);
            this.resources = CloneUtil.cloneMapObject(template.resources);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Mono<NotificationTemplate> extractDifference(NotificationTemplate inc) { // NOSONAR
            if (inc == null)
                return Mono.just(new NotificationTemplate());

            return FlatMapUtil.flatMapMonoWithNull(
                    () -> DifferenceExtractor.extract(inc.templateParts, this.templateParts),
                    tempParts -> DifferenceExtractor.extract(inc.resources, this.resources),
                    (tempParts, resource) -> {
                        this.templateParts = (Map<String, Map<String, String>>) tempParts;
                        this.resources = (Map<String, String>) resource;

                        return Mono.just(this);
                    });
        }

        @SuppressWarnings("unchecked")
        @Override
        public Mono<NotificationTemplate> applyOverride(NotificationTemplate override) { // NOSONAR
            if (override == null)
                return Mono.just(new NotificationTemplate());

            return FlatMapUtil.flatMapMonoWithNull(
                            () -> Mono.just(this),
                            obj -> DifferenceApplicator.apply(override.templateParts, this.templateParts),
                            (obj, tempParts) -> DifferenceApplicator.apply(override.resources, this.resources),
                            (obj, tempParts, resource) -> {
                                obj.templateParts = (Map<String, Map<String, String>>) tempParts;
                                obj.resources = (Map<String, String>) resource;

                                return Mono.just(obj);
                            })
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationTemplate.applyOverride"));
        }
    }
}
