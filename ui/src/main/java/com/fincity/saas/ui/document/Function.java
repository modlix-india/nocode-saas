package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "filterFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Function extends AbstractOverridableDTO<Function> {

	private static final long serialVersionUID = 2733397732360134939L;

	private FunctionDefinition definition;

	public Function(Function fun) {
		super(fun);
		this.definition = fun.definition == null ? null : new FunctionDefinition(fun.definition);
	}

	@Override
	public Mono<Function> applyOverride(Function base) {

		if (base != null)
			return DifferenceApplicator.apply(this.definition, base.definition)
			        .map(a ->
					{
				        this.definition = (FunctionDefinition) a;
				        return this;
			        });

		return Mono.just(this);
	}

	@Override
	public Mono<Function> makeOverride(Function base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(this.definition, e.definition)
		                .map(k ->
						{
			                e.definition = (FunctionDefinition) k;
			                return e;
		                }));
	}
}