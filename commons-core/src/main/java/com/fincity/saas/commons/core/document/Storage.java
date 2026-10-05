package com.fincity.saas.commons.core.document;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.StorageTriggerType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
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

    @Serial
    private static final long serialVersionUID = -5399288837130565200L;

    private Map<String, Object> schema;
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
    private Map<String, Object> fieldDefinitionMap;

    /**
     * Physical storage overrides per field, per backend.
     *
     * Only for fields whose schema type does not pin down how they should be stored -
     * a DECIMAL needs a precision and a scale that no schema carries. Absent, the type
     * mapper decides, which is what every storage does today.
     *
     * Not the same thing as {@link #fieldDefinitionMap}, which belongs to the form
     * designer and holds labels and editor types.
     */
    /**
     * How much row history to keep. Null means the platform default; 0 means keep
     * everything.
     *
     * Both bounds apply, and a version row has to satisfy BOTH to survive: it must
     * be among the most recent {@code versionRetentionCount} AND newer than
     * {@code versionRetentionDays}. Either one alone leaves a hole - a count alone
     * keeps a decade of history for a row touched twice a year, and an age alone
     * keeps a million versions of a row rewritten every minute.
     *
     * They live on the definition rather than in config because the right answer is
     * per storage: an audit-bearing ledger and a scratch cache have no business
     * sharing a retention policy.
     */
    private Integer versionRetentionDays;

    private Integer versionRetentionCount;

    private Map<String, StorageColumnDefinition> columnDefinitions;
    private Map<String, StorageIndex> indexes;
    private List<String> textIndexFields;

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
        this.columnDefinitions = CloneUtil.cloneMapObject(store.columnDefinitions);

        this.triggers = CloneUtil.cloneMapObject(store.triggers);

        this.indexes = CloneUtil.cloneMapObject(store.indexes);
        this.textIndexFields = CloneUtil.cloneMapList(store.textIndexFields);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Storage> applyOverride(Storage base) {
        if (base == null) return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> DifferenceApplicator.apply(this.schema, base.schema),
                        s -> DifferenceApplicator.apply(this.relations, base.relations),
                        (s, r) -> DifferenceApplicator.apply(this.triggers, base.triggers),
                        (s, r, t) -> DifferenceApplicator.apply(this.fieldDefinitionMap, base.fieldDefinitionMap),
                        (s, r, t, f) -> DifferenceApplicator.apply(this.indexes, base.indexes),
                        // Not through DifferenceApplicator. Its generic apply treats
                        // a null override as "delete this", which is the right rule
                        // for a key inside a delta map and the wrong one for a whole
                        // field a derived document simply never mentioned: the Map
                        // overload inherits the base in that case and this one did
                        // not, so a client below the one declaring textIndexFields
                        // lost them entirely and silently got no text index.
                        //
                        // A declared list replaces rather than merges. Two ordered
                        // lists have no sensible merge, and the fields a text index
                        // covers are a set the author chose together.
                        (s, r, t, f, i) -> this.textIndexFields == null
                                ? Mono.justOrEmpty(base.textIndexFields)
                                : Mono.just(this.textIndexFields),
                        (s, r, t, f, i, tif) -> {
                            this.schema = (Map<String, Object>) s;
                            this.relations = (Map<String, StorageRelation>) r;
                            this.triggers = (Map<StorageTriggerType, List<String>>) t;
                            this.fieldDefinitionMap = (Map<String, Object>) f;
                            this.indexes = (Map<String, StorageIndex>) i;
                            this.textIndexFields = (List<String>) tif;

                            this.subApplyOverride(base);

                            // Chained rather than taken as another argument: the
                            // helper is already at the arity it supports here, and one
                            // more field is not worth a shape nobody can read.
                            return DifferenceApplicator.apply(this.columnDefinitions, base.columnDefinitions)
                                    .map(cd -> (Map<String, StorageColumnDefinition>) cd)
                                    .defaultIfEmpty(Map.of())
                                    .map(cd -> {
                                        this.columnDefinitions = cd.isEmpty() ? null : cd;
                                        return this;
                                    });
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Storage.applyOverride"));
    }

    private void subApplyOverride(Storage base) {
        if (this.isAudited == null) this.isAudited = base.isAudited;

        if (this.isVersioned == null) this.isVersioned = base.isVersioned;

        if (this.createAuth == null) this.createAuth = base.createAuth;

        if (this.readAuth == null) this.readAuth = base.readAuth;

        if (this.updateAuth == null) this.updateAuth = base.updateAuth;

        if (this.deleteAuth == null) this.deleteAuth = base.deleteAuth;

        if (this.uniqueName == null) this.uniqueName = base.uniqueName;

        if (this.isAppLevel == null) this.isAppLevel = base.isAppLevel;

        if (this.onlyThruKIRun == null) this.onlyThruKIRun = base.onlyThruKIRun;

        if (this.generateEvents == null) this.generateEvents = base.generateEvents;

        if (this.versionRetentionDays == null) this.versionRetentionDays = base.versionRetentionDays;

        if (this.versionRetentionCount == null) this.versionRetentionCount = base.versionRetentionCount;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Storage> extractDifference(Storage base) {
        if (base == null) return Mono.just(this);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> Mono.just(this),
                        obj -> DifferenceExtractor.extract(obj.schema, base.schema),
                        (obj, sch) -> DifferenceExtractor.extract(obj.relations, base.relations),
                        (obj, sch, rel) -> DifferenceExtractor.extract(obj.triggers, base.triggers),
                        (obj, sch, rel, t) ->
                                DifferenceExtractor.extract(obj.fieldDefinitionMap, base.fieldDefinitionMap),
                        (obj, sch, rel, t, f) -> DifferenceExtractor.extract(obj.indexes, base.indexes),
                        (obj, sch, rel, t, f, i) ->
                                DifferenceExtractor.extract(obj.textIndexFields, base.textIndexFields),
                        (obj, sch, rel, t, f, i, tif) -> {
                            obj.setSchema((Map<String, Object>) sch);
                            obj.setRelations((Map<String, StorageRelation>) rel);
                            obj.setTriggers((Map<StorageTriggerType, List<String>>) t);
                            obj.setFieldDefinitionMap((Map<String, Object>) f);
                            obj.setIndexes((Map<String, StorageIndex>) i);
                            obj.setTextIndexFields((List<String>) tif);

                            this.subMakeOverride(base, obj);

                            return DifferenceExtractor.extract(obj.columnDefinitions, base.columnDefinitions)
                                    .map(cd -> (Map<String, StorageColumnDefinition>) cd)
                                    .defaultIfEmpty(Map.of())
                                    .map(cd -> {
                                        obj.setColumnDefinitions(cd.isEmpty() ? null : cd);
                                        return obj;
                                    });
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "Storage.makeOverride"));
    }

    private void subMakeOverride(Storage base, Storage obj) {
        if (CommonsUtil.safeEquals(obj.isAudited, base.isAudited)) obj.isAudited = null;

        if (CommonsUtil.safeEquals(obj.isVersioned, base.isVersioned)) obj.isVersioned = null;

        if (CommonsUtil.safeEquals(obj.createAuth, base.createAuth)) obj.createAuth = null;

        if (CommonsUtil.safeEquals(obj.readAuth, base.readAuth)) obj.readAuth = null;

        if (CommonsUtil.safeEquals(obj.updateAuth, base.updateAuth)) obj.updateAuth = null;

        if (CommonsUtil.safeEquals(obj.deleteAuth, base.deleteAuth)) obj.deleteAuth = null;

        if (CommonsUtil.safeEquals(obj.uniqueName, base.uniqueName)) obj.uniqueName = null;

        if (CommonsUtil.safeEquals(obj.isAppLevel, base.isAppLevel)) obj.isAppLevel = null;

        if (CommonsUtil.safeEquals(obj.onlyThruKIRun, base.onlyThruKIRun)) obj.onlyThruKIRun = null;

        if (CommonsUtil.safeEquals(obj.generateEvents, base.generateEvents)) obj.generateEvents = null;

        if (CommonsUtil.safeEquals(obj.versionRetentionDays, base.versionRetentionDays))
            obj.versionRetentionDays = null;

        if (CommonsUtil.safeEquals(obj.versionRetentionCount, base.versionRetentionCount))
            obj.versionRetentionCount = null;
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class StorageIndex implements Serializable {

        private List<StorageIndexField> fields;
        private boolean unique = false;

        public StorageIndex(StorageIndex sIndex) {
            this.fields = CloneUtil.cloneMapList(sIndex.fields);
            this.unique = sIndex.unique;
        }
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class StorageIndexField implements Serializable {

        private String fieldName;
        private Sort.Direction direction = Sort.Direction.ASC;

        public StorageIndexField(StorageIndexField sIndex) {
            this.fieldName = sIndex.fieldName;
            this.direction = sIndex.direction;
        }
    }
}
