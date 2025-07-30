package com.fincity.saas.message.dto;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import java.io.Serial;
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
public class ProviderIdentifier extends BaseUpdatableDto<ProviderIdentifier> {

    @Serial
    private static final long serialVersionUID = 4465432911601637027L;

    private ConnectionType connectionType;
    private ConnectionSubType connectionSubType;
    private String identifier;
    private boolean isDefault;
}
