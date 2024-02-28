package com.fincity.saas.core.document;

import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.mongo.util.DifferenceExtractor;
import com.fincity.saas.commons.util.EqualsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.enums.StorageTriggerType;
import com.fincity.saas.core.model.StorageRelation;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "storageFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Storage extends AbstractOverridableDTO<Storage> {

	private static final long serialVersionUID = -5399288837130565200L;

	private Map<String, Object> schema; // NOSONAR
	private String uniqueName;
	private Boolean isAudited = false;
	private Boolean isVersioned = false;
	private Boolean isAppLevel = false;
	private Boolean onlyThruKIRun = false;
	private String createAuth;
	private String readAuth;
	private String updateAuth;
	private String deleteAuth;
	private Map<String, StorageRelation> relations;
	private Boolean generateEvents;
	private Map<StorageTriggerType, List<String>> triggers;
	private Map<String, Object> fieldDefinitionMap; // NOSONAR

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
		this.onlyThruKIRun = store.onlyThruKIRun;
		this.relations = CloneUtil.cloneMapObject(store.relations);
		this.generateEvents = store.generateEvents;
		this.fieldDefinitionMap = CloneUtil.cloneMapObject(store.fieldDefinitionMap);

		this.triggers = CloneUtil.cloneMapObject(store.triggers);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Storage> applyOverride(Storage base) {

		if (base != null) {

			return FlatMapUtil.flatMapMonoWithNull(

					() -> DifferenceApplicator.apply(this.schema, base.schema),

					s -> DifferenceApplicator.apply(this.relations, base.relations),

					(s, r) -> DifferenceApplicator.apply(this.triggers, base.triggers),

					(s, r, t) -> DifferenceApplicator.apply(this.fieldDefinitionMap, base.fieldDefinitionMap),

					(s, r, t, f) -> {
						this.schema = (Map<String, Object>) s;
						this.relations = (Map<String, StorageRelation>) r;
						this.triggers = (Map<StorageTriggerType, List<String>>) t;
						this.fieldDefinitionMap = (Map<String, Object>) f;

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

		if (this.onlyThruKIRun == null)
			this.onlyThruKIRun = base.onlyThruKIRun;

		if (this.generateEvents == null)
			this.generateEvents = base.generateEvents;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Storage> makeOverride(Storage base) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> Mono.just(this),

				obj -> DifferenceExtractor.extract(obj.schema, base.schema),

				(obj, sch) -> DifferenceExtractor.extract(obj.relations, base.relations),

				(obj, sch, rel) -> DifferenceExtractor.extract(obj.triggers, base.triggers),

				(obj, sch, rel, t) -> DifferenceExtractor.extract(obj.fieldDefinitionMap, base.fieldDefinitionMap),

				(obj, sch, rel, t, f) -> {
					obj.setSchema((Map<String, Object>) sch);
					obj.setRelations((Map<String, StorageRelation>) rel);
					obj.setTriggers((Map<StorageTriggerType, List<String>>) t);
					obj.setFieldDefinitionMap((Map<String, Object>) f);

					this.subMakeOverride(base, obj);

					return Mono.just(obj);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "Storage.makeOverride"));
	}

	private void subMakeOverride(Storage base, Storage obj) {

		if (EqualsUtil.safeEquals(obj.isAudited, base.isAudited))
			obj.isAudited = null;

		if (EqualsUtil.safeEquals(obj.isVersioned, base.isVersioned))
			obj.isVersioned = null;

		if (EqualsUtil.safeEquals(obj.createAuth, base.createAuth))
			obj.createAuth = null;

		if (EqualsUtil.safeEquals(obj.readAuth, base.readAuth))
			obj.readAuth = null;

		if (EqualsUtil.safeEquals(obj.updateAuth, base.updateAuth))
			obj.updateAuth = null;

		if (EqualsUtil.safeEquals(obj.deleteAuth, base.deleteAuth))
			obj.deleteAuth = null;

		if (EqualsUtil.safeEquals(obj.uniqueName, base.uniqueName))
			obj.uniqueName = null;

		if (EqualsUtil.safeEquals(obj.isAppLevel, base.isAppLevel))
			obj.isAppLevel = null;

		if (EqualsUtil.safeEquals(obj.onlyThruKIRun, base.onlyThruKIRun))
			obj.onlyThruKIRun = null;

		if (EqualsUtil.safeEquals(obj.generateEvents, base.generateEvents))
			obj.generateEvents = null;
	}
}