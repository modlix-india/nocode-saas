package com.fincity.saas.commons.mongo.document;

import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class AbstractFiller<D extends AbstractFiller<D>> extends AbstractOverridableDTO<D> {

	private static final long serialVersionUID = 2089418665068611650L;

	private Map<String, Object> definition; // NOSONAR
	private Map<String, Object> values; // NOSONAR

	protected AbstractFiller(D fun) {
		super(fun);
		this.definition = CloneUtil.cloneMapObject(fun.getDefinition());
		this.values = CloneUtil.cloneMapObject(fun.getValues());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<D> applyOverride(D base) {

		if (base == null)
			return Mono.just((D) this);

		return FlatMapUtil.flatMapMono(

				() -> DifferenceApplicator.apply(this.definition, base.getDefinition()),

				d -> DifferenceApplicator.apply(this.values, base.getValues()),

				(d, v) -> {
					this.definition = (Map<String, Object>) d;
					this.values = (Map<String, Object>) v;
					return Mono.just((D) this);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFiller.applyOverride"));

	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<D> makeOverride(D base) {

		if (base == null)
			return Mono.just((D) this);

		return FlatMapUtil.flatMapMono(

				() -> Mono.just(this),

				e -> DifferenceExtractor.extract(e.definition, base.getDefinition()),

				(e, d) -> DifferenceExtractor.extract(e.values, base.getValues()),

				(e, d, v) -> {
					e.definition = (Map<String, Object>) d;
					e.values = (Map<String, Object>) v;
					return Mono.just((D) e);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFiller.makeOverride"));
	}
}
