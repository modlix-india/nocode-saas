package com.fincity.saas.message.dto;

import com.fincity.saas.message.dto.base.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WebhookLog extends BaseDto<WebhookLog> {

    private static final long serialVersionUID = 1L;
}
