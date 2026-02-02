package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.product.ProductRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
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
@IgnoreGeneration
public class Product extends BaseProcessorDto<Product> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private ULong productTemplateId;
    private boolean forPartner;
    private boolean overrideCTemplate = Boolean.TRUE;
    private boolean overrideRuTemplate = Boolean.TRUE;
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
        this.overrideCTemplate = product.overrideCTemplate;
        this.overrideRuTemplate = product.overrideRuTemplate;
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
}
