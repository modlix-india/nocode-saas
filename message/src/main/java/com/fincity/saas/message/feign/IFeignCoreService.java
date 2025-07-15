package com.fincity.saas.message.feign;

import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.document.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

public interface IFeignCoreService {

    String CONNECTION_PATH = "/api/core/connections/internal";

    String MESSAGE_PATH = "/api/core/messages/internal";

    @GetMapping(CONNECTION_PATH)
    Mono<Connection> getConnection(
            @RequestParam String urlClientCode,
            @RequestParam String connectionName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String connectionType);

    @GetMapping(MESSAGE_PATH)
    Mono<Message> getMessageInfo(
            @RequestParam String urlClientCode,
            @RequestParam String messageName,
            @RequestParam String appCode,
            @RequestParam String clientCode);
}
