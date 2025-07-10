package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@NoArgsConstructor
public class ComponentDefinition implements Serializable, IDifferentiable<ComponentDefinition> {

    private static final long serialVersionUID = -8719079119317757579L;

    private String key;
    private String name;
    private String type;
    private Map<String, Object> properties; // NOSONAR
    private Map<String, Object> styleProperties; // NOSONAR
    private boolean override;
    private Map<String, Boolean> children;
    private Integer displayOrder;
    private Map<String, String> bindingPath;
    private Map<String, String> bindingPath2;
    private Map<String, String> bindingPath3;
    private Map<String, String> bindingPath4;
    private Map<String, String> bindingPath5;
    private Map<String, String> bindingPath6;

    public ComponentDefinition(ComponentDefinition cd) {
        this.key = cd.key;
        this.name = cd.name;
        this.type = cd.type;
        this.override = cd.override;
        this.properties = CloneUtil.cloneMapObject(cd.properties);
        this.styleProperties = CloneUtil.cloneMapObject(cd.styleProperties);
        this.displayOrder = cd.displayOrder;
        this.children = CloneUtil.cloneMapObject(cd.children);
        this.bindingPath = CloneUtil.cloneMapObject(cd.bindingPath);
        this.bindingPath2 = CloneUtil.cloneMapObject(cd.bindingPath2);
        this.bindingPath3 = CloneUtil.cloneMapObject(cd.bindingPath3);
        this.bindingPath4 = CloneUtil.cloneMapObject(cd.bindingPath4);
        this.bindingPath5 = CloneUtil.cloneMapObject(cd.bindingPath5);
        this.bindingPath6 = CloneUtil.cloneMapObject(cd.bindingPath6);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<ComponentDefinition> extractDifference(ComponentDefinition incoming) { // NOSONAR
        // Cannot split the logic

        return FlatMapUtil.flatMapMono(

                        () -> DifferenceExtractor.extract(incoming.getProperties(), this.getProperties())
                                .defaultIfEmpty(Map.of()),

                        propDiff -> DifferenceExtractor.extractMapBoolean(incoming.getChildren(), this.getChildren())
                                .defaultIfEmpty(Map.of()),

                        (propDiff, childDiff) -> DifferenceExtractor
                                .extract(incoming.getStyleProperties(), this.getStyleProperties())
                                .defaultIfEmpty(Map.of()),

                        (propDiff, childDiff, styleDiff) ->

                                FlatMapUtil.flatMapMonoConsolidate(
                                                () -> DifferenceExtractor.extract(incoming.getBindingPath(), this.getBindingPath())
                                                        .defaultIfEmpty(Map.of()),

                                                b1 -> DifferenceExtractor.extract(incoming.getBindingPath2(), this.getBindingPath2())
                                                        .defaultIfEmpty(Map.of()),

                                                (b1, b2) -> DifferenceExtractor.extract(incoming.getBindingPath3(), this.getBindingPath3())
                                                        .defaultIfEmpty(Map.of()),

                                                (b1, b2, b3) -> DifferenceExtractor.extract(incoming.getBindingPath4(), this.getBindingPath4())
                                                        .defaultIfEmpty(Map.of()),

                                                (b1, b2, b3, b4) -> DifferenceExtractor
                                                        .extract(incoming.getBindingPath5(), this.getBindingPath5())
                                                        .defaultIfEmpty(Map.of()),

                                                (b1, b2, b3, b4, b5) -> DifferenceExtractor
                                                        .extract(incoming.getBindingPath6(), this.getBindingPath6())
                                                        .defaultIfEmpty(Map.of()))
                                        .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComponentDefinition.extractDifference")),

                        (propDiff, childDiff, styleDiff, bPaths) -> {

                            ComponentDefinition cd = new ComponentDefinition();
                            cd.setName(incoming.getName()
                                    .equals(this.getName()) ? null : this.getName());
                            cd.setOverride(true);
                            cd.setType(incoming.getType()
                                    .equals(this.getType()) ? null : this.getType());
                            cd.setProperties((Map<String, Object>) propDiff);
                            cd.setChildren(childDiff);
                            cd.setStyleProperties((Map<String, Object>) styleDiff);
                            if (CommonsUtil.safeEquals(this.displayOrder, incoming.displayOrder))
                                cd.setDisplayOrder(null);
                            else
                                cd.setDisplayOrder(incoming.displayOrder);

                            if (!bPaths.getT1()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT1());

                            if (!bPaths.getT2()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT2());

                            if (!bPaths.getT3()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT3());

                            if (!bPaths.getT4()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT4());

                            if (!bPaths.getT5()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT5());

                            if (!bPaths.getT6()
                                    .isEmpty())
                                cd.setBindingPath((Map<String, String>) bPaths.getT6());

                            return Mono.just(cd);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComponentDefinition.extractDifference"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ComponentDefinition> applyOverride(ComponentDefinition base) { // NOSONAR
        // Cannot split the logic
        if (base == null)
            return this.isOverride() ? Mono.empty() : Mono.justOrEmpty(this);

        return FlatMapUtil.flatMapMonoWithNull(

                        () -> DifferenceApplicator.apply(this.getProperties(), base.getProperties()),

                        propMap -> DifferenceApplicator.applyMapBoolean(this.getChildren(), base.getChildren()),

                        (propMap, childMap) -> DifferenceApplicator.apply(this.getStyleProperties(), base.getStyleProperties()),

                        (propMap, childMap, stylePropMap) -> DifferenceApplicator.apply(this.getBindingPath(),
                                base.getBindingPath()),

                        (propMap, childMap, stylePropMap, bPath1) -> DifferenceApplicator.apply(this.getBindingPath2(),
                                base.getBindingPath2()),

                        (propMap, childMap, stylePropMap, bPath1, bPath2) -> DifferenceApplicator.apply(this.getBindingPath3(),
                                base.getBindingPath3()),

                        (propMap, childMap, stylePropMap, bPath1, bPath2, bPath3) -> DifferenceApplicator
                                .apply(this.getBindingPath4(), base.getBindingPath4()),

                        (propMap, childMap, stylePropMap, bPath1, bPath2, bPath3, bPath4) -> DifferenceApplicator
                                .apply(this.getBindingPath5(), base.getBindingPath5()),

                        (propMap, childMap, stylePropMap, bPath1, bPath2, bPath3, bPath4, bPath5) -> DifferenceApplicator
                                .apply(this.getBindingPath6(), base.getBindingPath6()),

                        (propMap, childMap, stylePropMap, bPath1, bPath2, bPath3, bPath4, bPath5, bPath6) -> {

                            this.setChildren(childMap);
                            this.setProperties((Map<String, Object>) propMap);
                            this.setStyleProperties((Map<String, Object>) stylePropMap);
                            this.setKey(base.getKey());
                            this.setOverride(true);
                            if (this.getType() == null)
                                this.setType(base.getType());
                            if (this.getName() == null)
                                this.setName(base.getName());
                            if (this.getDisplayOrder() == null)
                                this.setDisplayOrder(base.getDisplayOrder());

                            if (bPath1 != null && !bPath1.isEmpty())
                                this.setBindingPath((Map<String, String>) bPath1);

                            if (bPath2 != null && !bPath2.isEmpty())
                                this.setBindingPath2((Map<String, String>) bPath2);

                            if (bPath3 != null && !bPath3.isEmpty())
                                this.setBindingPath3((Map<String, String>) bPath3);

                            if (bPath4 != null && !bPath4.isEmpty())
                                this.setBindingPath4((Map<String, String>) bPath4);

                            if (bPath5 != null && !bPath5.isEmpty())
                                this.setBindingPath5((Map<String, String>) bPath5);

                            if (bPath6 != null && !bPath6.isEmpty())
                                this.setBindingPath6((Map<String, String>) bPath6);

                            return Mono.justOrEmpty(this);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComponentDefinition.applyOverride"));
    }
}
