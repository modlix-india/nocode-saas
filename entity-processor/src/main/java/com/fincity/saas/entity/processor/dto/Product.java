package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.google.gson.JsonPrimitive;
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
public class Product extends BaseProcessorDto<Product> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private ULong productTemplateId;
    private boolean forPartner;
    private ULong productWalkInFormId;
    private FileDetail logoFileDetail;
    private FileDetail bannerFileDetail;

    public Product() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
        this.relationsMap.put(Fields.productWalkInFormId, EntitySeries.PRODUCT_WALK_IN_FORMS.getTable());
    }

    public Product(Product product) {
        super(product);
        this.productTemplateId = product.productTemplateId;
        this.forPartner = product.forPartner;
        this.productWalkInFormId = product.productWalkInFormId;
        this.logoFileDetail = product.logoFileDetail;
        this.bannerFileDetail = product.bannerFileDetail;
    }

    public static Product of(ProductRequest productRequest) {
        return new Product()
                .setName(productRequest.getName())
                .setDescription(productRequest.getDescription())
                .setForPartner(productRequest.getForPartner())
                .setLogoFileDetail(productRequest.getLogoFileDetail())
                .setBannerFileDetail(productRequest.getBannerFileDetail());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.productTemplateId,
                Schema.ofLong(Fields.productTemplateId).setMinimum(1));
        props.put(Fields.forPartner, Schema.ofBoolean(Fields.forPartner).setDefaultValue(new JsonPrimitive(false)));
        props.put(
                Fields.productWalkInFormId,
                Schema.ofLong(Fields.productWalkInFormId).setMinimum(1));
        props.put(Fields.logoFileDetail, Schema.ofObject(Fields.logoFileDetail));
        props.put(Fields.bannerFileDetail, Schema.ofObject(Fields.bannerFileDetail));

        schema.setProperties(props);
        return schema;
    }
}
