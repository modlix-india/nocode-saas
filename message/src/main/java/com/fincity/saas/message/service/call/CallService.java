package com.fincity.saas.message.service.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.call.CallDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageConnectionService;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.exotel.ExotelCallService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Service for making calls using different call service providers.
 */
@Service
public class CallService extends BaseUpdatableService<CallRecord, Call, CallDAO> {

    private final MessageConnectionService connectionService;
    private final ExotelCallService exotelCallService;

    private final EnumMap<ConnectionSubType, IAppCallService> services = new EnumMap<>(ConnectionSubType.class);

    public CallService(
            ExotelCallService exotelCallService,
            MessageConnectionService connectionService,
            MessageResourceService msgService) {
        this.exotelCallService = exotelCallService;
        this.connectionService = connectionService;
        this.msgService = msgService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.MESSAGE_EXOTEL, exotelCallService);
    }

    public Mono<Map<String, Object>> makeCall(
            MessageAccess messageAccess, PhoneNumber from, PhoneNumber to, String callerId, String connectionName) {
        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode), actup -> {
                            MessageAccess access = MessageAccess.of(actup.getT1(), actup.getT2(), true);
                            return connectionService
                                    .getMessageConn(actup.getT1(), actup.getT2(), connectionName)
                                    .switchIfEmpty(msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                            MessageResourceService.CONNECTION_DETAILS_MISSING,
                                            connectionName))
                                    .flatMap(conn -> Mono.justOrEmpty(this.services.get(conn.getConnectionSubType()))
                                            .switchIfEmpty(msgService.throwMessage(
                                                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                                    MessageResourceService.CONNECTION_DETAILS_MISSING,
                                                    conn.getConnectionSubType()))
                                            .flatMap(callService -> callService.makeCallAndSave(
                                                    access, fromNumber, toNumber, callerId, connectionName)));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCallAndSave"));
    }
}
