package com.fincity.saas.entity.processor.oserver.message.model;

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
    private static final long serialVersionUID = 914541620041638673L;

    private Map<String, String> providerIncomingRequest;
}
