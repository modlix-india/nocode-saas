package com.fincity.saas.message.service.message;

import com.fincity.saas.message.dao.message.MessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageMessagesRecord;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import org.springframework.stereotype.Service;

/**
 * CRUD over the generic message record.
 *
 * <p>This used to be a provider dispatcher: a {@code sendMessage} that looked up a connection,
 * resolved its subtype and handed off to a per-provider service. WhatsApp Cloud API was the only
 * provider ever registered, so with it retired the dispatcher had exactly zero implementations and
 * {@code sendMessage} could only ever have thrown.
 *
 * <p>The dispatcher was deleted rather than left empty. An extension point with no implementations
 * is not a seam, it is a trap: the next person adds SMS behind it and inherits a design shaped
 * entirely around Meta's Graph API, including a webhook service and a callback-URL scheme that only
 * made sense there. Whatever comes next should be built against what it actually needs.
 *
 * <p>WhatsApp does not pass through here at all now. It leaves through the bridge client and comes
 * back through the dispatch outbox.
 */
@Service
public class MessageService extends BaseUpdatableService<MessageMessagesRecord, Message, MessageDAO> {

    private static final String MESSAGE_CACHE = "message";

    @Override
    protected String getCacheName() {
        return MESSAGE_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.MESSAGE;
    }
}
