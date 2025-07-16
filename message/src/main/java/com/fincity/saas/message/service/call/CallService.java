package com.fincity.saas.message.service.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.call.CallDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CallService extends BaseUpdatableService<MessageCallsRecord, Call, CallDAO> {

    private static final String CALL_CACHE = "call";

    private final CallConnectionService connectionService;
    private final ExotelCallService exotelCallService;

    private final EnumMap<ConnectionSubType, IAppCallService<?>> services = new EnumMap<>(ConnectionSubType.class);

    public CallService(
            ExotelCallService exotelCallService,
            CallConnectionService connectionService,
            MessageResourceService msgService) {
        this.exotelCallService = exotelCallService;
        this.connectionService = connectionService;
        this.msgService = msgService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.CALL_EXOTEL, exotelCallService);
    }

    @Override
    protected String getCacheName() {
        return CALL_CACHE;
    }

    public Mono<Call> makeCall(CallRequest callRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.connectionService.getConnection(
                                access.getAppCode(), callRequest.getConnectionName(), access.getClientCode()),
                        (access, connection) -> services.get(connection.getConnectionSubType())
                                .makeCall(access, callRequest, connection),
                        (access, connection, call) -> this.createInternal(access, call))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCall"));
    }
}
