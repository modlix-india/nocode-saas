package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProductWalkInForm extends BaseUpdatableDto<ProductWalkInForm> {

    @Serial
    private static final long serialVersionUID = -6827340490522807962L;

    private ULong productId;
    private ULong stageId;
    private ULong statusId;
    private AssignmentType assignmentType;

    public ProductWalkInForm() {
        super();
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.statusId, EntitySeries.STAGE.getTable());
    }

    public ProductWalkInForm(ProductWalkInForm productWalkInForm) {
        super(productWalkInForm);
        this.productId = productWalkInForm.productId;
        this.stageId = productWalkInForm.stageId;
        this.statusId = productWalkInForm.statusId;
        this.assignmentType = productWalkInForm.assignmentType;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_WALK_IN_FORMS;
    }

    public static ProductWalkInForm of(ULong productId, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        return new ProductWalkInForm()
                .setProductId(productId)
                .setStageId(stageId)
                .setStatusId(statusId)
                .setAssignmentType(assignmentType);
    }
}
