package com.fincity.saas.commons.mongo.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "schemeFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Schema extends AbstractOverridableDTO<Schema> {

	private static final long serialVersionUID = 2089418665068611650L;

	private Map<String, Object> definition; //NOSONAR

	public Schema(Schema fun) {
		super(fun);
		this.definition = CloneUtil.cloneMapObject(definition);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Schema> applyOverride(Schema base) {

		if (base != null)
			return DifferenceApplicator.apply(this.definition, base.definition)
			        .map(a ->
					{
				        this.definition = (Map<String, Object>) a;
				        return this;
			        });

		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Schema> makeOverride(Schema base) {

		if (base == null)
			return Mono.just(this);

		return Mono.just(this)
		        .flatMap(e -> DifferenceExtractor.extract(this.definition, e.definition)
		                .map(k ->
						{
			                e.definition = (Map<String, Object>) k;
			                return e;
		                }));
	}
}