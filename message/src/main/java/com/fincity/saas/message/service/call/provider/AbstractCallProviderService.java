package com.fincity.saas.message.service.call.provider;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.dao.call.provider.AbstractCallProviderDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.CallService;
import com.fincity.saas.message.service.call.IAppCallService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.util.Map;

public abstract class AbstractCallProviderService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends AbstractCallProviderDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements IAppCallService<D> {

    public static final String CALL_BACK_URI = "/api/call/callback";
    protected CallService callService;
    protected CallConnectionService callConnectionService;

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

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CALL_PARAMETERS,
                this.getProvider(),
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

    protected Mono<String> getRequiredConnectionDetail(Map<String, Object> details, String key) {
        String val = (String) details.get(key);
        if (val == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CONNECTION_DETAILS,
                    this.getProvider(),
                    key);
        return Mono.just(val);
    }

    protected Mono<D> findByUniqueField(String id) {
        return this.dao.findByUniqueField(id);
    }

    protected Mono<String> getCallBackUrl(String appCode, String clientCode) {
        return this.securityService
                .getAppUrl(appCode, clientCode)
                .map(appUrl -> appUrl + CALL_BACK_URI + this.getProviderUri());
    }
}
