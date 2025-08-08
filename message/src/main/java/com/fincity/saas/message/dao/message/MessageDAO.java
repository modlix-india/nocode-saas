package com.fincity.saas.message.dao.message;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_MESSAGES;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.jooq.tables.records.MessageMessagesRecord;
import org.springframework.stereotype.Component;

@Component
public class MessageDAO extends BaseUpdatableDAO<MessageMessagesRecord, Message> {

    public MessageDAO() {
        super(Message.class, MESSAGE_MESSAGES, MESSAGE_MESSAGES.ID);
    }
}
