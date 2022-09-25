package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "themeFilteringIndex")
@Accessors(chain = true)
public class Theme extends AbstractUIDTO<Theme> {

	private static final long serialVersionUID = 4355909627072800292L;

	private Map<String, Map<String, Object>> styles; // NOSONAR
	private Map<String, Map<String, String>> variables;
	private Map<String, Map<String, Object>> variableGroups; // NOSONAR

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Theme> applyOverride(Theme base) {

		if (base != null) {
			this.styles = (Map<String, Map<String, Object>>) DifferenceApplicator.jsonMap(this.styles, base.styles);
			this.variables = (Map<String, Map<String, String>>) DifferenceApplicator.jsonMap(this.variables,
			        base.variables);
			this.variableGroups = (Map<String, Map<String, Object>>) DifferenceApplicator.jsonMap(this.variableGroups,
			        base.variableGroups);
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Theme> makeOverride(Theme base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.styles, base.styles)
		                .map(e ->
						{
			                a.setStyles((Map<String, Map<String, Object>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.variables, base.variables)
		                .map(e ->
						{
			                a.setVariables((Map<String, Map<String, String>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.jsonMap(a.variableGroups, base.variableGroups)
		                .map(e ->
						{
			                a.setVariableGroups((Map<String, Map<String, Object>>) e);
			                return a;
		                }));

	}
}
