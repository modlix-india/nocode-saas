package com.fincity.saas.message.dao.call.provider.exotel;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_EXOTEL_CALLS;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import org.springframework.stereotype.Component;

@Component
public class ExotelDAO extends BaseProviderDAO<MessageExotelCallsRecord, ExotelCall> {

    protected ExotelDAO() {
        super(ExotelCall.class, MESSAGE_EXOTEL_CALLS, MESSAGE_EXOTEL_CALLS.ID, MESSAGE_EXOTEL_CALLS.SID);
    }
}
