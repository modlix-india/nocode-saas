package com.fincity.saas.commons.mongo.document;

import java.util.Map;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class AbstractSchema<D extends AbstractSchema<D>> extends AbstractOverridableDTO<D> {

	private static final long serialVersionUID = 2089418665068611650L;

	private Map<String, Object> definition; // NOSONAR

	protected AbstractSchema(D fun) {
		super(fun);
		this.definition = CloneUtil.cloneMapObject(fun.getDefinition());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<D> applyOverride(D base) {

		if (base != null)
			return DifferenceApplicator.apply(this.definition, base.getDefinition())
					.map(a -> {
						this.definition = (Map<String, Object>) a;
						return (D) this;
					});

		return Mono.just((D) this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<D> makeOverride(D base) {

		if (base == null)
			return Mono.just((D) this);

		return Mono.just(this)
				.flatMap(e -> DifferenceExtractor.extract(e.definition, base.getDefinition())
						.map(k -> {
							e.definition = (Map<String, Object>) k;
							return (D) e;
						}));
	}
}