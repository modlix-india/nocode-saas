package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

import java.io.Serial;
import java.math.BigInteger;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProductTemplateWalkInForm extends BaseUpdatableDto<ProductTemplateWalkInForm> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong productTemplateId;
    private ULong stageId;
    private ULong statusId;
    private AssignmentType assignmentType;

    public ProductTemplateWalkInForm(){
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
        this.relationsMap.put(Fields.stageId,EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.statusId,EntitySeries.STAGE.getTable());
    }

    public ProductTemplateWalkInForm(ProductTemplateWalkInForm productTemplateWalkInForm){
        super(productTemplateWalkInForm);
        this.productTemplateId = productTemplateWalkInForm.productTemplateId;
        this.stageId = productTemplateWalkInForm.stageId;
        this.statusId = productTemplateWalkInForm.statusId;
        this.assignmentType = productTemplateWalkInForm.assignmentType;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE_WALK_IN_FORM;
    }

    public static ProductTemplateWalkInForm of(ProductTemplateWalkInFormRequest productTemplateWalkInFormRequest){

        return (ProductTemplateWalkInForm) new ProductTemplateWalkInForm()
                .setName(productTemplateWalkInFormRequest.getName())
                .setDescription(productTemplateWalkInFormRequest.getDescription())
                .setProductTemplateId(productTemplateWalkInFormRequest.getProductTemplateId().getULongId())
                .setStageId(productTemplateWalkInFormRequest.getStageId().getULongId())
                .setStatusId(productTemplateWalkInFormRequest.getStatusId().getULongId())
                .setAssignmentType(productTemplateWalkInFormRequest.getAssignmentType())
                .setId(productTemplateWalkInFormRequest.getId().getULongId());
    }

}
