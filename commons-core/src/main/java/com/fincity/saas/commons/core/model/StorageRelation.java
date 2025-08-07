package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StorageRelation implements Serializable, IDifferentiable<StorageRelation> {

    @Serial
    private static final long serialVersionUID = 4819827598636692079L;

    private String uniqueRelationId;
    private String storageName;
    private StorageRelationType relationType;
    private String fieldName;
    private StorageRelationConstraint deleteConstraint = StorageRelationConstraint.NOTHING;
    private StorageRelationConstraint updateConstraint = StorageRelationConstraint.NOTHING;

    public StorageRelation(StorageRelation relation) {
        this.uniqueRelationId = relation.uniqueRelationId;
        this.storageName = relation.storageName;
        this.relationType = relation.relationType;
        this.fieldName = relation.fieldName;
        this.deleteConstraint = relation.deleteConstraint;
        this.updateConstraint = relation.updateConstraint;
    }

    @Override
    public Mono<StorageRelation> extractDifference(StorageRelation inc) {
        if (inc == null) return Mono.just(this);

        StorageRelation diff = new StorageRelation();

        diff.uniqueRelationId =
                !CommonsUtil.safeEquals(this.uniqueRelationId, inc.uniqueRelationId) ? null : this.uniqueRelationId;

        diff.storageName = !CommonsUtil.safeEquals(this.storageName, inc.storageName) ? null : this.storageName;

        diff.relationType = !CommonsUtil.safeEquals(this.relationType, inc.relationType) ? null : this.relationType;

        diff.fieldName = !CommonsUtil.safeEquals(this.fieldName, inc.fieldName) ? null : this.fieldName;

        diff.deleteConstraint =
                !CommonsUtil.safeEquals(this.deleteConstraint, inc.deleteConstraint) ? null : this.deleteConstraint;

        diff.updateConstraint =
                !CommonsUtil.safeEquals(this.updateConstraint, inc.updateConstraint) ? null : this.updateConstraint;

        return Mono.just(diff).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageRelation.extractDifference"));
    }

    @Override
    public Mono<StorageRelation> applyOverride(StorageRelation override) {
        if (override == null) return Mono.just(this);

        if (this.uniqueRelationId == null) this.uniqueRelationId = override.uniqueRelationId;

        if (this.storageName == null) this.storageName = override.storageName;

        if (this.relationType == null) this.relationType = override.relationType;

        if (this.fieldName == null) this.fieldName = override.fieldName;

        if (this.deleteConstraint == null) this.deleteConstraint = override.deleteConstraint;

        if (this.updateConstraint == null) this.updateConstraint = override.updateConstraint;

        return Mono.just(this).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageRelation.applyOverride"));
    }
}
