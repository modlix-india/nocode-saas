package com.fincity.saas.message.model.request.call;

import com.fincity.saas.message.model.request.BaseMessageRequest;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class IncomingCallRequest extends BaseMessageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4574664927812256563L;

    private Map<String, Object> providerIncomingRequest;
}
