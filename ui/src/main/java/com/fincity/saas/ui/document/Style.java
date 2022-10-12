package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "styleFilteringIndex")
@Accessors(chain = true)
public class Style extends AbstractUIDTO<Style> {

	private static final long serialVersionUID = 4355909627072800292L;

	private Map<String, Map<String, Object>> styles; // NOSONAR
	private Map<String, Map<String, String>> variables;
	private Map<String, Map<String, Object>> variableGroups; // NOSONAR

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Style> applyOverride(Style base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(

			        () -> DifferenceApplicator.apply(this.styles, base.styles),

			        s -> DifferenceApplicator.apply(this.variables, base.variables),

			        (s, v) -> DifferenceApplicator.apply(this.variableGroups, base.variableGroups),

			        (s, v, vg) ->
					{
				        this.styles = (Map<String, Map<String, Object>>) s;
				        this.variableGroups = (Map<String, Map<String, Object>>) vg;
				        this.variables = (Map<String, Map<String, String>>) v;

				        return Mono.just(this);
			        });
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Style> makeOverride(Style base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(a -> DifferenceExtractor.extract(a.styles, base.styles)
		                .map(e ->
						{
			                a.setStyles((Map<String, Map<String, Object>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.extract(a.variables, base.variables)
		                .map(e ->
						{
			                a.setVariables((Map<String, Map<String, String>>) e);
			                return a;
		                }))
		        .flatMap(a -> DifferenceExtractor.extract(a.variableGroups, base.variableGroups)
		                .map(e ->
						{
			                a.setVariableGroups((Map<String, Map<String, Object>>) e);
			                return a;
		                }));

	}
}
