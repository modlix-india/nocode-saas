package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "storageFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Storage extends AbstractOverridableDTO<Storage> {

	private static final long serialVersionUID = -5399288837130565200L;

	private Map<String, Object> schema; // NOSONAR
	private Boolean isAudited;
	private Boolean isVersioned;

	public Storage(Storage store) {

		super(store);
		this.schema = CloneUtil.cloneMapObject(store.schema);
		this.isAudited = store.isAudited;
		this.isVersioned = store.isVersioned;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Storage> applyOverride(Storage base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(() -> DifferenceApplicator.apply(this.schema, base.schema),

			        s ->
					{
				        this.schema = (Map<String, Object>) s;

				        if (this.isAudited == null)
					        this.isAudited = base.isAudited;

				        if (this.isVersioned == null)
					        this.isVersioned = base.isVersioned;

				        return Mono.just(this);
			        });
		}
		return Mono.just(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Storage> makeOverride(Storage base) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> Mono.just(this),

		        obj -> DifferenceExtractor.extract(obj.schema, base.schema),

		        (obj, sch) ->
				{
			        obj.setSchema((Map<String, Object>) sch);

			        if (obj.isAudited != null && obj.isAudited.equals(base.isAudited))
				        obj.isAudited = null;

			        if (obj.isVersioned != null && obj.isVersioned.equals(base.isVersioned))
				        obj.isVersioned = null;

			        return Mono.just(obj);
		        }

		);
	}
}
