package com.fincity.saas.message.service.call;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import reactor.core.publisher.Mono;

public interface IAppCallService<D extends BaseUpdatableDto<D>> {

    String getProvider();

    String getProviderUri();

    Mono<Call> toCall(D providerObject);

    Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection);
}
