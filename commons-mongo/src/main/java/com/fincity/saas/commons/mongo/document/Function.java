package com.fincity.saas.commons.mongo.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "functionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Function extends AbstractOverridableDTO<Function> {

	private static final long serialVersionUID = 2733397732360134939L;

	private Map<String, Object> definition; // NOSONAR
	private String executeAuth;

	public Function(Function fun) {
		super(fun);
		this.definition = CloneUtil.cloneMapObject(definition);
		this.executeAuth = fun.executeAuth;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Function> applyOverride(Function base) {

		if (base != null)
			return DifferenceApplicator.apply(this.definition, base.definition)
			        .map(a ->
					{
				        this.definition = (Map<String, Object>) a;
				        if (this.executeAuth == null)
					        this.executeAuth = base.executeAuth;
				        return this;
			        });

		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Function> makeOverride(Function base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(this.definition, e.definition)
		                .map(k ->
						{
			                e.definition = (Map<String, Object>) k;

			                if (this.executeAuth != null && this.executeAuth.equals(base.executeAuth))
				                this.executeAuth = null;

			                return e;
		                }));
	}
}