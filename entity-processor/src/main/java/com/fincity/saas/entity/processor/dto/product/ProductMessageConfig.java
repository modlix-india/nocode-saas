package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
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
public class ProductMessageConfig extends BaseUpdatableDto<ProductMessageConfig> {

    @Serial
    private static final long serialVersionUID = 3294737603455853726L;

    private ULong productId;
    private ULong stageId;
    private ULong statusId;

    private MessageChannelType channel;
    private Integer order;

    private ULong messageTemplateId;

    public ProductMessageConfig() {
        super();
    }

    public ProductMessageConfig(ProductMessageConfig other) {
        super(other);
        this.productId = other.productId;
        this.stageId = other.stageId;
        this.statusId = other.statusId;
        this.channel = other.channel;
        this.order = other.order;
        this.messageTemplateId = other.messageTemplateId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_MESSAGE_CONFIGS;
    }
}
