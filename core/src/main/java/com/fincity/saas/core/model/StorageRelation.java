package com.fincity.saas.core.model;

import java.io.Serializable;

import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.util.EqualsUtil;
import com.fincity.saas.core.enums.StorageRelationConstraint;
import com.fincity.saas.core.enums.StorageRelationType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StorageRelation implements Serializable, IDifferentiable<StorageRelation> {

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
    public Mono<StorageRelation> applyOverride(StorageRelation base) {

        if (base == null)
            return Mono.just(this);

        if (this.uniqueRelationId == null)
            this.uniqueRelationId = base.uniqueRelationId;

        if (this.storageName == null)
            this.storageName = base.storageName;

        if (this.relationType == null)
            this.relationType = base.relationType;

        if (this.fieldName == null)
            this.fieldName = base.fieldName;

        if (this.deleteConstraint == null)
            this.deleteConstraint = base.deleteConstraint;

        if (this.updateConstraint == null)
            this.updateConstraint = base.updateConstraint;

        return Mono.just(this);
    }

    @Override
    public Mono<StorageRelation> extractDifference(StorageRelation inc) {

        if (inc == null)
            return Mono.just(this);

        StorageRelation diff = new StorageRelation();

        if (!EqualsUtil.safeEquals(this.uniqueRelationId, inc.uniqueRelationId))
            diff.uniqueRelationId = null;
        else
            diff.uniqueRelationId = this.uniqueRelationId;

        if (!EqualsUtil.safeEquals(this.storageName, inc.storageName))
            diff.storageName = null;
        else
            diff.storageName = this.storageName;

        if (!EqualsUtil.safeEquals(this.relationType, inc.relationType))
            diff.relationType = null;
        else
            diff.relationType = this.relationType;

        if (!EqualsUtil.safeEquals(this.fieldName, inc.fieldName))
            diff.fieldName = null;
        else
            diff.fieldName = this.fieldName;

        if (!EqualsUtil.safeEquals(this.deleteConstraint, inc.deleteConstraint))
            diff.deleteConstraint = null;
        else
            diff.deleteConstraint = this.deleteConstraint;

        if (!EqualsUtil.safeEquals(this.updateConstraint, inc.updateConstraint))
            diff.updateConstraint = null;
        else
            diff.updateConstraint = this.updateConstraint;

        return Mono.just(diff);
    }
}
