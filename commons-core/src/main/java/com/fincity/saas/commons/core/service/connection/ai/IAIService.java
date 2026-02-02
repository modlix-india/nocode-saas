package com.fincity.saas.commons.core.service.connection.ai;

import com.fincity.saas.commons.core.document.Connection;
import reactor.core.publisher.Mono;

public interface IAIService {

    Mono<String> chat(Connection connection, String prompt);
}
