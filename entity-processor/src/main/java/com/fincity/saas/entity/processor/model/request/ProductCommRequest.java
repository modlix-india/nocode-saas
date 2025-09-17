package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductCommRequest extends BaseRequest<ProductCommRequest> {

    @Serial
    private static final long serialVersionUID = 2363790716459331018L;

    private Identity productId;
    private String connectionName;
    private ConnectionType connectionType;
    private PhoneNumber phoneNumber;
    private Email email;
    private Boolean isDefault;
}
