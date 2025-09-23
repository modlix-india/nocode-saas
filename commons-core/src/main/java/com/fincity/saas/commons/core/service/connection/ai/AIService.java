package com.fincity.saas.commons.core.service.connection.ai;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.connection.appdata.IAppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.EnumMap;

@Service
public class AIService {

    private final EnumMap<ConnectionSubType, IAIService> services = new EnumMap<>(ConnectionSubType.class);

    private final ConnectionService connectionService;
    private final CoreMessageResourceService msgService;

    public AIService(OpenAIService openAIService, ConnectionService connectionService, CoreMessageResourceService msgService) {

        services.put(ConnectionSubType.OPENAI, openAIService);
        this.connectionService = connectionService;
        this.msgService = msgService;
    }

    public Mono<String> chat(
            String appCode,
            String clientCode,
            String connectionName,
            String prompt) {

        return FlatMapUtil.flatMapMono(

                () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),

                tuple -> connectionService
                        .read(connectionName, tuple.getT1(), tuple.getT2(), ConnectionType.AI)
                        .switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                CoreMessageResourceService.CONNECTION_DETAILS_MISSING, connectionName)),

                (tuple, conn) -> Mono.justOrEmpty(this.services.get(conn.getConnectionSubType()))
                        .switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                CoreMessageResourceService.CONNECTION_DETAILS_MISSING, conn.getConnectionSubType())),

                (tuple, conn, aiService) -> aiService.chat(conn, prompt)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AIService.chat"));
    }
}
