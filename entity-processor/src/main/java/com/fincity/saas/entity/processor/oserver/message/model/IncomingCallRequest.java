package com.fincity.saas.entity.processor.oserver.message.model;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.util.MultiValueMap;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class IncomingCallRequest extends BaseMessageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 914541620041638673L;

    private MultiValueMap<String, String> providerIncomingRequest;
}
