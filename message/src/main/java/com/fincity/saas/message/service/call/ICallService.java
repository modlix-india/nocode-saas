package com.fincity.saas.message.service.call;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import reactor.core.publisher.Mono;

public interface ICallService<D extends BaseUpdatableDto<D>> {

    ConnectionType getConnectionType();

    ConnectionSubType getConnectionSubType();

    String getProviderUri();

    default Mono<Call> toCall(D providerObject) {
        return Mono.empty();
    }

    default Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {
        return Mono.empty();
    }

    /**
     * Places a call for a service that has already checked the caller may make it. Returns the provider-shaped
     * call, since the caller keys its record on the provider's call id, which {@link Call} cannot carry.
     */
    Mono<?> makeCallInternal(String appCode, String clientCode, CallRequest callRequest, String ownerService);

    /** Answers the provider's inbound request with whom to ring, in the provider's own reply format. */
    Mono<?> connectCall(String appCode, String clientCode, IncomingCallRequest request);
}
