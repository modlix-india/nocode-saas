package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

import java.io.Serial;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Campaign extends BaseUpdatableDto<Campaign> {

    @Serial
    private static final long serialVersionUID = -6274489567525261523L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private String campaignSource;
    private ULong productId;

}
