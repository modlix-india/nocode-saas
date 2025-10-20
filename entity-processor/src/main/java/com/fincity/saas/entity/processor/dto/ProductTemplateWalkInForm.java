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
public class ProductTemplateWalkInForm extends BaseUpdatableDto<ProductTemplateWalkInForm> {

    @Serial
    private static final long serialVersionUID = 1667873650332251053L;

    private ULong productTemplateId;
    private ULong stageId;
    private ULong statusId;
    private AssignmentType assignmentType;

    public ProductTemplateWalkInForm() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.statusId, EntitySeries.STAGE.getTable());
    }

    public ProductTemplateWalkInForm(ProductTemplateWalkInForm productTemplateWalkInForm) {
        super(productTemplateWalkInForm);
        this.productTemplateId = productTemplateWalkInForm.productTemplateId;
        this.stageId = productTemplateWalkInForm.stageId;
        this.statusId = productTemplateWalkInForm.statusId;
        this.assignmentType = productTemplateWalkInForm.assignmentType;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE_WALK_IN_FORMS;
    }

    public static ProductTemplateWalkInForm of(
            ULong productTemplateId, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        return new ProductTemplateWalkInForm()
                .setProductTemplateId(productTemplateId)
                .setStageId(stageId)
                .setStatusId(statusId)
                .setAssignmentType(assignmentType);
    }
}
