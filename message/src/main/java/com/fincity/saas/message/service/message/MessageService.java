package com.fincity.saas.message.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.MessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageMessagesRecord;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageRequest;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MessageService extends BaseUpdatableService<MessageMessagesRecord, Message, MessageDAO> {

    private static final String MESSAGE_CACHE = "message";

    private final MessageConnectionService connectionService;
    private final WhatsappMessageService whatsappMessageService;

    private final EnumMap<ConnectionSubType, IMessageService<?>> services = new EnumMap<>(ConnectionSubType.class);

    public MessageService(MessageConnectionService connectionService, WhatsappMessageService whatsappMessageService) {
        this.connectionService = connectionService;
        this.whatsappMessageService = whatsappMessageService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.WHATSAPP, whatsappMessageService);
    }

    @Override
    protected String getCacheName() {
        return MESSAGE_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.MESSAGE;
    }

    public Mono<Message> sendMessage(MessageRequest messageRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.connectionService.getConnection(
                                access.getAppCode(), access.getClientCode(), messageRequest.getConnectionName()),
                        (access, connection) -> services.get(connection.getConnectionSubType())
                                .sendMessage(access, messageRequest, connection),
                        (access, connection, message) -> this.createInternal(access, message))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageService.sendMessage"));
    }
}
