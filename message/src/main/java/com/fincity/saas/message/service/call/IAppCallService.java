package com.fincity.saas.message.service.call;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface IAppCallService {

    Mono<Map<String, Object>> makeCallAndSave(
            MessageAccess access, PhoneNumber fromNumber, PhoneNumber toNumber, String callerId, String connectionName);
}
