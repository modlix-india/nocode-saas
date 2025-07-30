package com.fincity.saas.message.service.call;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import reactor.core.publisher.Mono;

public interface ICallService<D extends BaseUpdatableDto<D>> {

    ConnectionType getConnectionType();

    ConnectionSubType getConnectionSubType();

    String getProviderUri();

    Mono<Call> toCall(D providerObject);

    Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection);
}
