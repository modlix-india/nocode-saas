package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.model.ComponentDefinition;

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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "pageFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Page extends AbstractOverridableDTO<Page> {

    private static final long serialVersionUID = 6899134951550453853L;

    private String device;
    private Map<String, Map<String, String>> translations;
    private Map<String, Object> properties; // NOSONAR
    private Map<String, Object> eventFunctions; // NOSONAR
    private String rootComponent;
    private Map<String, ComponentDefinition> componentDefinition;

    public Page(Page page) {

        super(page);
        this.device = page.device;
        this.translations = CloneUtil.cloneMapStringMap(page.translations);
        this.properties = CloneUtil.cloneMapObject(page.properties);
        this.eventFunctions = CloneUtil.cloneMapObject(page.eventFunctions);
        this.rootComponent = page.rootComponent;
        this.componentDefinition = CloneUtil.cloneMapObject(page.componentDefinition);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Page> applyOverride(Page base) {

        if (base != null) {

            return FlatMapUtil
                    .flatMapMonoWithNull(() -> DifferenceApplicator.apply(this.translations, base.translations),

                            t -> DifferenceApplicator.apply(this.properties, base.properties),

                            (t, p) -> DifferenceApplicator.apply(this.eventFunctions, base.eventFunctions),

                            (t, p, e) -> DifferenceApplicator.apply(this.componentDefinition, base.componentDefinition),

                            (t, p, e, c) -> {
                                this.translations = (Map<String, Map<String, String>>) t;
                                this.properties = (Map<String, Object>) p;
                                this.eventFunctions = (Map<String, Object>) e;
                                this.componentDefinition = (Map<String, ComponentDefinition>) c;

                                this.device = base.device;
                                if (this.rootComponent == null)
                                    this.rootComponent = base.rootComponent;

                                return Mono.just(this);
                            })
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "Page.applyOverride"));
        }
        return Mono.just(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Page> extractDifference(Page base) {

        return FlatMapUtil.flatMapMonoWithNull(

                        () -> Mono.just(this),

                        obj -> DifferenceExtractor.extract(obj.properties, base.properties),

                        (obj, props) -> DifferenceExtractor.extract(obj.translations, base.translations),

                        (obj, props, trans) -> DifferenceExtractor.extract(obj.componentDefinition, base.componentDefinition),

                        (obj, props, trans, cd) -> DifferenceExtractor.extract(obj.eventFunctions, base.eventFunctions),

                        (obj, props, trans, cd, evs) -> {

                            obj.setProperties((Map<String, Object>) props);
                            obj.setTranslations((Map<String, Map<String, String>>) trans);
                            obj.setComponentDefinition((Map<String, ComponentDefinition>) cd);
                            obj.setEventFunctions((Map<String, Object>) evs);

                            if (obj.rootComponent != null && obj.rootComponent.equals(base.rootComponent))
                                obj.rootComponent = null;
                            return Mono.just(obj);
                        }

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Page.makeOverride"));
    }
}
