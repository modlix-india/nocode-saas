package com.fincity.saas.message.dao.message;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_MESSAGE_WEBHOOKS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.message.MessageWebhook;
import com.fincity.saas.message.jooq.tables.records.MessageMessageWebhooksRecord;
import org.springframework.stereotype.Component;

@Component
public class MessageWebhookDAO extends BaseUpdatableDAO<MessageMessageWebhooksRecord, MessageWebhook> {

    public MessageWebhookDAO() {
        super(MessageWebhook.class, MESSAGE_MESSAGE_WEBHOOKS, MESSAGE_MESSAGE_WEBHOOKS.ID);
    }
}
