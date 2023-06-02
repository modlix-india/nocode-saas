package com.fincity.saas.core.document;

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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "eventDefinitionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class EventDefinition extends AbstractOverridableDTO<EventDefinition> {

	private static final long serialVersionUID = -5343026916526769179L;

	private Map<String, Object> schema; // NOSONAR

	public EventDefinition(EventDefinition obj) {

		super(obj);
		this.schema = CloneUtil.cloneMapObject(obj.schema);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventDefinition> applyOverride(EventDefinition base) {

		if (base != null)
			return DifferenceApplicator.apply(this.schema, base.schema)
			        .map(a ->
					{
				        this.schema = (Map<String, Object>) a;
				        return this;
			        });

		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<EventDefinition> makeOverride(EventDefinition base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(e.schema, base.schema)
		                .map(k ->
						{
			                e.schema = (Map<String, Object>) k;
			                return e;
		                }));
	}
}