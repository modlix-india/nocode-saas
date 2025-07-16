package com.fincity.saas.message.service.call.provider;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.IAppCallService;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class AbstractCallProviderService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseUpdatableDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements IAppCallService<D> {

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return this.getMsgService()
                .throwMessage(
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
        if (val == null) {
            return this.getMsgService()
                    .throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.MISSING_CONNECTION_DETAILS,
                            this.getProvider(),
                            key);
        }
        return Mono.just(val);
    }
}
