package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;

import lombok.Data;
import reactor.core.publisher.Mono;

@Data
public class ComponentDefinition implements Serializable, IDifferentiable<ComponentDefinition> {

	private static final long serialVersionUID = -8719079119317757579L;

	private String key;
	private String name;
	private String type;
	private Map<String, Object> properties; // NOSONAR
	private boolean override;
	private Map<String, Boolean> children;
	private String permission;

	@SuppressWarnings("unchecked")
	@Override
	public Mono<ComponentDefinition> extractDifference(ComponentDefinition incoming) {
		
		return FlatMapUtil.flatMapMono(

		        () -> DifferenceExtractor.extract(incoming.getProperties(), this.getProperties())
		                .defaultIfEmpty(Map.of()),

		        propDiff -> DifferenceExtractor.extractMapBoolean(incoming.getChildren(), this.getChildren())
		                .defaultIfEmpty(Map.of()),

		        (propDiff, childDiff) ->
				{

			        ComponentDefinition cd = new ComponentDefinition();
			        cd.setName(incoming.getName()
			                .equals(this.getName()) ? null : this.getName());
			        cd.setOverride(true);
			        cd.setType(incoming.getType()
			                .equals(this.getType()) ? null : this.getType());
			        cd.setProperties((Map<String, Object>) propDiff);
			        cd.setChildren(childDiff);

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

		        (propMap, childMap) ->
				{

			        this.setChildren(childMap);
			        this.setProperties((Map<String, Object>) propMap);
			        this.setKey(base.getKey());
			        this.setOverride(true);
			        if (this.getType() == null)
				        this.setType(base.getType());
			        if (this.getName() == null)
				        this.setName(base.getName());

			        return Mono.justOrEmpty(this);
		        });
	}
}
