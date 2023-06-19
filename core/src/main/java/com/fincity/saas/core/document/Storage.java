package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "storageFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Storage extends AbstractOverridableDTO<Storage> {

	private static final long serialVersionUID = -5399288837130565200L;

	private Map<String, Object> schema; // NOSONAR
	private String uniqueName;
	private Boolean isAudited = false;
	private Boolean isVersioned = false;
	private Boolean isAppLevel = false;
	private String createAuth;
	private String readAuth;
	private String updateAuth;
	private String deleteAuth;

	public Storage(Storage store) {

		super(store);
		this.schema = CloneUtil.cloneMapObject(store.schema);
		this.isAudited = store.isAudited;
		this.isVersioned = store.isVersioned;

		this.createAuth = store.createAuth;
		this.readAuth = store.readAuth;
		this.updateAuth = store.updateAuth;
		this.deleteAuth = store.deleteAuth;
		this.uniqueName = store.uniqueName;
		this.isAppLevel = store.isAppLevel;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Storage> applyOverride(Storage base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(() -> DifferenceApplicator.apply(this.schema, base.schema),

			        s ->
					{
				        this.schema = (Map<String, Object>) s;

				        this.subApplyOverride(base);

				        return Mono.just(this);
			        })
			        .contextWrite(Context.of(LogUtil.METHOD_NAME, "Storage.applyOverride"));
		}
		return Mono.just(this);
	}

	private void subApplyOverride(Storage base) {

		if (this.isAudited == null)
			this.isAudited = base.isAudited;

		if (this.isVersioned == null)
			this.isVersioned = base.isVersioned;

		if (this.createAuth == null)
			this.createAuth = base.createAuth;

		if (this.readAuth == null)
			this.readAuth = base.readAuth;

		if (this.updateAuth == null)
			this.updateAuth = base.updateAuth;

		if (this.deleteAuth == null)
			this.deleteAuth = base.deleteAuth;

		if (this.uniqueName == null)
			this.uniqueName = base.uniqueName;

		if (this.isAppLevel == null)
			this.isAppLevel = base.isAppLevel;
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

			        this.subMakeOverride(base, obj);

			        return Mono.just(obj);
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "Storage.makeOverride"));
	}

	private void subMakeOverride(Storage base, Storage obj) {

		if (obj.isAudited != null && obj.isAudited.equals(base.isAudited))
			obj.isAudited = null;

		if (obj.isVersioned != null && obj.isVersioned.equals(base.isVersioned))
			obj.isVersioned = null;

		if (obj.createAuth != null && obj.createAuth.equals(base.createAuth))
			obj.createAuth = null;

		if (obj.readAuth != null && obj.readAuth.equals(base.readAuth))
			obj.readAuth = null;

		if (obj.updateAuth != null && obj.updateAuth.equals(base.updateAuth))
			obj.updateAuth = null;

		if (obj.deleteAuth != null && obj.deleteAuth.equals(base.deleteAuth))
			obj.deleteAuth = null;

		if (obj.uniqueName != null && obj.uniqueName.equals(base.uniqueName))
			obj.uniqueName = null;

		obj.isAppLevel = base.isAppLevel;
	}
}
