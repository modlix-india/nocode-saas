package com.fincity.saas.message.service.connection.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.EnumMap;

@Service
public class CallService {

    private final ConnectionService connectionService;
    private final CoreMessageResourceService msgService;
    private final ExotelCallService exotelCallService;

    private final EnumMap<ConnectionSubType, IAppCallService> services = new EnumMap<>(ConnectionSubType.class);

    public CallService(
            ExotelCallService exotelCallService,
            ConnectionService connectionService,
            CoreMessageResourceService msgService) {
        this.exotelCallService = exotelCallService;
        this.connectionService = connectionService;
        this.msgService = msgService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.EXOTEL, exotelCallService);
    }

    public Mono<Boolean> makeCall(
            String appCode,
            String clientCode,
            String fromNumber,
            String toNumber,
            String callerId,
            String connectionName) {
        
        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                actup -> connectionService
                        .read(connectionName, actup.getT1(), actup.getT2(), ConnectionType.CALL)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                                connectionName)),
                (actup, conn) -> Mono.justOrEmpty(this.services.get(conn.getConnectionSubType()))
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                                conn.getConnectionSubType())),
                (actup, conn, callService) -> callService.makeCall(fromNumber, toNumber, callerId, conn))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCall"));
    }
}
