package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "pageFilteringIndex")
@Accessors(chain = true)
public class Page extends AbstractUIDTO<Page> {

	private static final long serialVersionUID = 6899134951550453853L;

	private String device;
	private Map<String, Map<String, String>> translations;
	private Map<String, Object> properties; // NOSONAR
	private Map<String, FunctionDefinition> eventFunctions;
	private String rootComponent;
	private Map<String, ComponentDefinition> componentDefinition;

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Page> applyOverride(Page base) {

		if (base != null) {
			this.translations = (Map<String, Map<String, String>>) DifferenceApplicator.jsonMap(this.translations,
			        base.translations);
			this.properties = (Map<String, Object>) DifferenceApplicator.jsonMap(this.properties, base.properties);
			this.eventFunctions = (Map<String, FunctionDefinition>) DifferenceApplicator.jsonMap(this.eventFunctions,
			        base.eventFunctions);
			this.componentDefinition = (Map<String, ComponentDefinition>) DifferenceApplicator
			        .jsonMap(this.componentDefinition, base.componentDefinition);
			this.device = base.device;
			if (this.rootComponent == null)
				this.rootComponent = base.rootComponent;
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Page> makeOverride(Page base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.translations, base.translations)
		                .map(e ->
						{
			                a.setTranslations((Map<String, Map<String, String>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.properties, base.properties)
		                .map(e ->
						{
			                a.setProperties((Map<String, Object>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.componentDefinition, base.componentDefinition)
		                .map(e ->
						{
			                a.setComponentDefinition((Map<String, ComponentDefinition>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.eventFunctions, base.eventFunctions)
		                .map(e ->
						{
			                a.setEventFunctions((Map<String, FunctionDefinition>) e);
			                return a;
		                }))
		        .map(a -> {
		        	
		        	if (a.rootComponent !=null && a.rootComponent.equals(base.rootComponent))
		        		a.rootComponent = null;
		        	return a;
		        });
	}
}
