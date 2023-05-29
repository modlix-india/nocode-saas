package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.core.model.EventActionTask;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "eventActionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class EventAction extends AbstractOverridableDTO<EventAction> {
	
	private static final long serialVersionUID = 8419515774158611099L;
	
	private Map<String, EventActionTask> tasks;

	public EventAction(EventAction obj) {

		super(obj);
		this.tasks = CloneUtil.cloneMapObject(obj.tasks);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventAction> applyOverride(EventAction base) {

		if (base != null)
			return DifferenceApplicator.apply(this.tasks, base.tasks)
			        .map(a ->
					{
				        this.tasks = (Map<String, EventActionTask>) a;
				        return this;
			        });

		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventAction> makeOverride(EventAction base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(e.tasks, base.tasks)
		                .map(k ->
						{
			                e.tasks = (Map<String, EventActionTask>) k;
			                return e;
		                }));
	}
}
