package com.fincity.saas.message.service.connection.call;

import com.fincity.saas.commons.core.document.Connection;
import reactor.core.publisher.Mono;

public interface IAppCallService {

    Mono<Boolean> makeCall(
            String fromNumber, 
            String toNumber, 
            String callerId, 
            Connection connection);
}
