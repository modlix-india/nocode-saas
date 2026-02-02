package com.fincity.saas.message.dao.call;

import static com.fincity.saas.message.jooq.tables.MessageCalls.MESSAGE_CALLS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import org.springframework.stereotype.Component;

@Component
public class CallDAO extends BaseUpdatableDAO<MessageCallsRecord, Call> {

    public CallDAO() {
        super(Call.class, MESSAGE_CALLS, MESSAGE_CALLS.ID);
    }
}
