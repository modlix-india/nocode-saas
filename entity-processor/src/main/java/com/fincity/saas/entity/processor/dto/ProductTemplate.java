package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.ProductTemplateType;
import com.fincity.saas.entity.processor.model.request.ProductTemplateRequest;
import java.io.Serial;
import java.util.Map;
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
public class ProductTemplate extends BaseUpdatableDto<ProductTemplate> {

    @Serial
    private static final long serialVersionUID = 2361640922389483322L;

    private ProductTemplateType productTemplateType;
    private ULong productTemplateWalkInFormId;

    public ProductTemplate() {
        super();
        this.relationsMap.put(
                Fields.productTemplateWalkInFormId, EntitySeries.PRODUCT_TEMPLATE_WALK_IN_FORMS.getTable());
    }

    public ProductTemplate(ProductTemplate productTemplate) {
        super(productTemplate);
        this.productTemplateType = productTemplate.productTemplateType;
        this.productTemplateWalkInFormId = productTemplate.productTemplateWalkInFormId;
    }

    public static ProductTemplate of(ProductTemplateRequest productTemplateRequest) {
        return new ProductTemplate()
                .setName(productTemplateRequest.getName())
                .setDescription(productTemplateRequest.getDescription())
                .setProductTemplateType(productTemplateRequest.getProductTemplateType());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.productTemplateType,
                Schema.ofString(Fields.productTemplateType)
                        .setEnums(EnumSchemaUtil.getSchemaEnums(ProductTemplateType.class)));
        props.put(
                Fields.productTemplateWalkInFormId,
                Schema.ofLong(Fields.productTemplateWalkInFormId).setMinimum(1));

        schema.setProperties(props);
        return schema;
    }
}
