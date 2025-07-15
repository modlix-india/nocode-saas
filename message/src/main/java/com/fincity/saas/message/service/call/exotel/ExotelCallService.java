package com.fincity.saas.message.service.call.exotel;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.saas.message.dao.call.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.exotel.ExotelCall;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;

import reactor.core.publisher.Mono;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    private static final String EXOTEL_API_URL_TEMPLATE = "https://%s:%s@%s/v1/Accounts/%s/Calls/connect";

    @Override
    public Mono<Map<String, Object>> makeCallAndSave(
            MessageAccess access,
            PhoneNumber fromNumber,
            PhoneNumber toNumber,
            String callerId,
            String connectionName) {
        return Mono.empty();
    }

    @Override
    protected String getCacheName() {
        return "";
    }
}
