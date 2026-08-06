package com.fincity.saas.message.controller.message;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.MessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.jooq.tables.records.MessageMessagesRecord;
import com.fincity.saas.message.service.message.MessageService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD over the generic message record.
 *
 * <p>{@code POST /send} went with the Cloud API. It dispatched to a provider by connection subtype
 * and WhatsApp was the only one registered, so once that was retired the route could only fail.
 * WhatsApp now sends through {@code /api/message/whatsapp/sessions/internal/{id}/messages}, which
 * routes to the bridge instance holding that session.
 */
@RestController
@RequestMapping("/api/message")
public class MessageController
        extends BaseUpdatableController<MessageMessagesRecord, Message, MessageDAO, MessageService> {}
