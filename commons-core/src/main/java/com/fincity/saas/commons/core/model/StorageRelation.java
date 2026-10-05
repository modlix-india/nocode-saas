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

    /**
     * The difference, where {@code this} is the BASE and {@code inc} is the derived
     * relation.
     *
     * The conditions were inverted and the wrong operand was kept: a value that
     * DIFFERED became null, and one that matched was copied into the delta. Both
     * halves are wrong, and together they meant a derived client could not change
     * an inherited relation at all - the save reported success and the relation
     * kept the base's values. For a delete constraint that decides whether a
     * referenced row can be removed, which is not a cosmetic difference.
     *
     * Safe to correct: no derived storage in the fleet holds relations, so nothing
     * stored depends on the old shape.
     */
    @Override
    public Mono<StorageRelation> extractDifference(StorageRelation inc) {

        // The derived relation says nothing, so it overrides nothing.
        if (inc == null) return Mono.just(new StorageRelation());

        StorageRelation diff = new StorageRelation();

        diff.uniqueRelationId =
                CommonsUtil.safeEquals(this.uniqueRelationId, inc.uniqueRelationId) ? null : inc.uniqueRelationId;

        diff.storageName = CommonsUtil.safeEquals(this.storageName, inc.storageName) ? null : inc.storageName;

        diff.relationType = CommonsUtil.safeEquals(this.relationType, inc.relationType) ? null : inc.relationType;

        diff.fieldName = CommonsUtil.safeEquals(this.fieldName, inc.fieldName) ? null : inc.fieldName;

        diff.deleteConstraint =
                CommonsUtil.safeEquals(this.deleteConstraint, inc.deleteConstraint) ? null : inc.deleteConstraint;

        diff.updateConstraint =
                CommonsUtil.safeEquals(this.updateConstraint, inc.updateConstraint) ? null : inc.updateConstraint;

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
