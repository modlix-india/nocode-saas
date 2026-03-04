package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import reactor.core.publisher.Mono;

public interface TicketChannelMessageService {

    MessageChannelType getChannel();

    Mono<Void> sendOnTicketCreate(ProcessorAccess access, Ticket ticket, ProductMessageConfig config);
}

