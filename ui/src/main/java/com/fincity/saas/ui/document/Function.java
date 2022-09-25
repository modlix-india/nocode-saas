package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "filterFilteringIndex")
@Accessors(chain = true)
public class Function extends AbstractUIDTO<Function> {

	private static final long serialVersionUID = 2733397732360134939L;

	private FunctionDefinition definition;

	@Override
	public Mono<Function> applyOverride(Function base) {

		if (base != null)
			this.definition = DifferenceApplicator.json(this.definition, base.definition);

		return Mono.just(this);
	}

	@Override
	public Mono<Function> makeOverride(Function base) {

		if (base == null)
			return Mono.just(this);
		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.json(this.definition, e.definition)
		                .map(k ->
						{

			                e.definition = (FunctionDefinition) k;
			                return e;
		                }));
	}
}