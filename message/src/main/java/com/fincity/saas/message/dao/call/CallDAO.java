package com.fincity.saas.message.dao.call;

import static com.fincity.saas.message.jooq.tables.MessageCalls.MESSAGE_CALLS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CallDAO extends BaseUpdatableDAO<MessageCallsRecord, Call> {

    public CallDAO() {
        super(Call.class, MESSAGE_CALLS, MESSAGE_CALLS.ID);
    }

    /** The latest call row an Exotel call was recorded under; {@code message_exotel_calls} has no connection. */
    public Mono<Call> findByExotelCallId(String appCode, String clientCode, ULong exotelCallId) {

        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_CALLS)
                        .where(MESSAGE_CALLS.APP_CODE.eq(appCode))
                        .and(MESSAGE_CALLS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_CALLS.EXOTEL_CALL_ID.eq(exotelCallId))
                        .orderBy(MESSAGE_CALLS.ID.desc())
                        .limit(1))
                .map(rec -> rec.into(Call.class));
    }
}
