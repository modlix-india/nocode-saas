package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.message.model.MessageTemplateQueObject;
import com.fincity.saas.entity.processor.service.ActivityService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketWhatsappTemplateMessageService implements TicketChannelMessageService {

    private final ActivityService activityService;
    private final TemplateEventPublisher templateEventPublisher;

    public TicketWhatsappTemplateMessageService(
            ActivityService activityService, TemplateEventPublisher templateEventPublisher) {
        this.activityService = activityService;
        this.templateEventPublisher = templateEventPublisher;
    }

    @Override
    public MessageChannelType getChannel() {
        return MessageChannelType.WHATS_APP_TEMPLATE;
    }

    @Override
    public Mono<Void> sendOnTicketCreate(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {

        int slotIndex = resolveSlotIndex(config);
        MessageTemplateQueObject q = toQueObject(access, ticket, config);

        return this.templateEventPublisher
                .publish(q, slotIndex)
                .then(this.activityService.acWhatsapp(ticket.getId(), null, ticket.getName()))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappTemplateMessageService.sendOnTicketCreate"));
    }

    private int resolveSlotIndex(ProductMessageConfig cfg) {
        Integer order = cfg.getOrder();
        return order != null && order >= 0 ? order : 0;
    }

    private MessageTemplateQueObject toQueObject(ProcessorAccess access, Ticket ticket, ProductMessageConfig cfg) {

        ULong ticketId = ticket.getId();
        ULong productId = ticket.getProductId();
        ULong stageId = ticket.getStage();
        ULong statusId = ticket.getStatus();

        return new MessageTemplateQueObject()
                .setEventName("TicketCreated")
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setTicketId(ticketId != null ? ticketId.toString() : null)
                .setProductId(productId != null ? productId.toString() : null)
                .setStageId(stageId != null ? stageId.toString() : null)
                .setStatusId(statusId != null ? statusId.toString() : null)
                .setChannel(cfg.getChannel() != null ? cfg.getChannel().getLiteral() : null)
                .setMessageTemplateId(
                        cfg.getMessageTemplateId() != null
                                ? cfg.getMessageTemplateId().toBigInteger().longValue()
                                : null);
    }
}
