package com.fincity.saas.message.service.call.provider;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.model.common.IdAndValue;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.CallService;
import com.fincity.saas.message.service.call.ICallService;
import com.fincity.saas.message.service.call.event.CallEventService;
import com.fincity.saas.message.util.PhoneUtil;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class AbstractCallProviderService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseProviderDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements ICallService<D> {

    public static final String CALL_BACK_URI = "/api/call/callback";
    protected CallService callService;
    protected CallConnectionService callConnectionService;
    protected CallEventService callEventService;
    protected WebClientConfig webClientConfig;

    @Lazy
    @Autowired
    private void setCallService(CallService callService) {
        this.callService = callService;
    }

    @Lazy
    @Autowired
    private void setCallConnectionService(CallConnectionService callConnectionService) {
        this.callConnectionService = callConnectionService;
    }

    @Lazy
    @Autowired
    private void setCallEventService(CallEventService callEventService) {
        this.callEventService = callEventService;
    }

    @Autowired
    private void setWebClientConfig(WebClientConfig webClientConfig) {
        this.webClientConfig = webClientConfig;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.CALL;
    }

    protected Mono<Boolean> isValidConnection(Connection connection) {
        if (connection.getConnectionType() != ConnectionType.CALL
                || !connection.getConnectionSubType().equals(this.getConnectionSubType()))
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.INVALID_CONNECTION_TYPE);

        return Mono.just(Boolean.TRUE);
    }

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CALL_PARAMETERS,
                this.getConnectionSubType().getProvider(),
                paramName);
    }

    protected <T> T getConnectionDetail(Map<String, Object> details, String key, Class<T> clazz) {
        Object val = details.get(key);
        if (val == null) return null;

        if (clazz.isInstance(val)) return clazz.cast(val);

        try {
            if (clazz == Integer.class) return clazz.cast(Integer.valueOf(val.toString()));
            if (clazz == Boolean.class) return clazz.cast(Boolean.valueOf(val.toString()));
            if (clazz == String.class) return clazz.cast(val.toString());
            if (clazz == String[].class) {
                if (val instanceof String s) return clazz.cast(s.split(","));
                if (val instanceof String[]) return clazz.cast(val);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot cast field " + key + " to " + clazz.getSimpleName());
        }

        return null;
    }

    protected Mono<D> findByUniqueField(String id) {
        return this.dao.findByUniqueField(id);
    }

    protected Mono<String> getCallBackUrl(String appCode, String clientCode) {
        return this.securityService
                .getAppUrl(appCode, clientCode)
                .map(appUrl -> appUrl + CALL_BACK_URI + this.getProviderUri())
                .switchIfEmpty(Mono.just(""));
    }

    protected Mono<IdAndValue<ULong, PhoneNumber>> getUserIdAndPhone(ULong userId) {
        return this.securityService
                .getUserInternal(userId.toBigInteger())
                .map(user -> IdAndValue.of(
                        ULongUtil.valueOf(user.get("id")), PhoneUtil.parse(String.valueOf(user.get("phoneNumber")))));
    }
}
