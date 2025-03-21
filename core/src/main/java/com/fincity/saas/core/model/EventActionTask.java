package com.fincity.saas.core.model;

import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.enums.EventActionTaskType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EventActionTask implements IDifferentiable<EventActionTask>, Comparable<EventActionTask>, Serializable {

	private static final long serialVersionUID = 667994849634307890L;

	private String key;
	private Integer order;
	private EventActionTaskType type;
	private Map<String, Object> parameters; // NOSONAR

	public EventActionTask(EventActionTask eat) {
		this.key = eat.key;
		this.order = eat.order;
		this.type = eat.type;
		this.parameters = CloneUtil.cloneMapObject(eat.parameters);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventActionTask> extractDifference(EventActionTask inc) {
		return FlatMapUtil.flatMapMono(

				() -> DifferenceExtractor.extract(inc.parameters, this.parameters)
						.defaultIfEmpty(Map.of()),

				params -> {

					EventActionTask eat = new EventActionTask();

					if (inc.order == null || !inc.order.equals(this.order))
						eat.order = this.order;

					if (inc.type != this.type)
						eat.type = this.type;

					eat.parameters = (Map<String, Object>) params;

					return Mono.just(eat);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "EventActionTask.extractDifference"));

	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventActionTask> applyOverride(EventActionTask base) {

		return base == null ? Mono.just(this)
				: FlatMapUtil.flatMapMono(

						() -> DifferenceApplicator.apply(this.parameters, base.parameters),

						params -> {

							if (this.key == null)
								this.key = base.key;

							if (this.order == null)
								this.order = base.order;

							if (this.type == null)
								this.type = base.type;

							this.setParameters((Map<String, Object>) params);

							return Mono.just(this);
						}

				)
						.contextWrite(Context.of(LogUtil.METHOD_NAME, "EventActionTask.applyOverride"));
	}

	@Override
	public int compareTo(EventActionTask o) {

		int compValue = Integer.compare(this.order == null ? 0 : this.order, o.order == null ? 0 : o.order);

		return compValue == 0 ? this.key.compareTo(o.key) : compValue;
	}

}
