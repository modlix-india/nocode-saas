package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ActivityService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketWhatsappTemplateMessageService implements TicketChannelMessageService {

    private final ActivityService activityService;

    public TicketWhatsappTemplateMessageService(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public MessageChannelType getChannel() {
        return MessageChannelType.WHATS_APP_TEMPLATE;
    }

    @Override
    public Mono<Void> sendOnTicketCreate(
            ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {

        // For now, just log a WhatsApp activity entry tied to this ticket.
        // The actual call to the message service / queue can be plugged in here.
        return this.activityService
                .acWhatsapp(ticket.getId(), null, ticket.getName())
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketWhatsappTemplateMessageService.sendOnTicketCreate"));
    }
}

