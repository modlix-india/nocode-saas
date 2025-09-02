package com.fincity.saas.message.service.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.call.CallDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
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

    private final EnumMap<ConnectionSubType, ICallService<?>> services = new EnumMap<>(ConnectionSubType.class);

    public CallService(CallConnectionService connectionService, ExotelCallService exotelCallService) {
        this.connectionService = connectionService;
        this.exotelCallService = exotelCallService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.EXOTEL, exotelCallService);
    }

    @Override
    protected String getCacheName() {
        return CALL_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.CALL;
    }

    public Mono<Call> makeCall(CallRequest callRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.connectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), callRequest.getConnectionName()),
                        (access, connection) -> services.get(connection.getConnectionSubType())
                                .makeCall(access, callRequest, connection),
                        (access, connection, call) -> this.createInternal(access, call))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCall"));
    }
}
