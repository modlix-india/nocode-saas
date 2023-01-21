package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

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
	private String permission;

	public ComponentDefinition(ComponentDefinition cd) {
		this.key = cd.key;
		this.name = cd.name;
		this.type = cd.type;
		this.override = cd.override;
		this.permission = cd.permission;
		this.properties = CloneUtil.cloneMapObject(cd.properties);
		this.styleProperties = CloneUtil.cloneMapObject(cd.styleProperties);
		this.displayOrder = cd.displayOrder;
		this.children = CloneUtil.cloneMapObject(cd.children);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<ComponentDefinition> extractDifference(ComponentDefinition incoming) {

		return FlatMapUtil.flatMapMono(

		        () -> DifferenceExtractor.extract(incoming.getProperties(), this.getProperties())
		                .defaultIfEmpty(Map.of()),

		        propDiff -> DifferenceExtractor.extractMapBoolean(incoming.getChildren(), this.getChildren())
		                .defaultIfEmpty(Map.of()),

		        (propDiff, childDiff) -> DifferenceExtractor
		                .extract(incoming.getStyleProperties(), this.getStyleProperties())
		                .defaultIfEmpty(Map.of()),

		        (propDiff, childDiff, styleDiff) ->
				{

			        ComponentDefinition cd = new ComponentDefinition();
			        cd.setName(incoming.getName()
			                .equals(this.getName()) ? null : this.getName());
			        cd.setOverride(true);
			        cd.setType(incoming.getType()
			                .equals(this.getType()) ? null : this.getType());
			        cd.setProperties((Map<String, Object>) propDiff);
			        cd.setChildren(childDiff);
			        cd.setStyleProperties((Map<String, Object>) styleDiff);
			        if (this.displayOrder == incoming.displayOrder)
			        	cd.setDisplayOrder(null);
			        else
			        	cd.setDisplayOrder(this.displayOrder);

			        return Mono.just(cd);
		        });
	}

	@SuppressWarnings("unchecked")
	public Mono<ComponentDefinition> applyOverride(ComponentDefinition base) {
		if (base == null)
			return this.isOverride() ? Mono.empty() : Mono.justOrEmpty(this);

		return FlatMapUtil.flatMapMono(

		        () -> DifferenceApplicator.apply(this.getProperties(), base.getProperties()),

		        propMap -> DifferenceApplicator.applyMapBoolean(this.getChildren(), base.getChildren()),

		        (propMap, childMap) -> DifferenceApplicator.apply(this.getStyleProperties(), base.getStyleProperties()),

		        (propMap, childMap, stylePropMap) ->
				{

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
			        
			        return Mono.justOrEmpty(this);
		        });
	}
}
