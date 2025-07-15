package com.fincity.saas.message.dao.call.exotel;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_EXOTEL_CALLS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.call.exotel.ExotelCall;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import org.springframework.stereotype.Component;

@Component
public class ExotelDAO extends BaseUpdatableDAO<MessageExotelCallsRecord, ExotelCall> {

    protected ExotelDAO() {
        super(ExotelCall.class, MESSAGE_EXOTEL_CALLS, MESSAGE_EXOTEL_CALLS.ID);
    }
}
