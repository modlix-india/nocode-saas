package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import reactor.core.publisher.Mono;

/**
 * One channel's handling of a stage rule.
 *
 * <p>Named for stages rather than for creation because a deal reaching a stage is the trigger, and
 * being created at one is simply the first time that happens. The previous name said "on ticket
 * create", which was accurate when rules only ever fired once and became actively misleading when
 * they started firing on every move.
 */
public interface TicketChannelMessageService {

    MessageChannelType getChannel();

    /**
     * Handles one matching rule for a deal that has arrived at a stage.
     *
     * <p>Queues rather than sends, on every channel that has pacing rules. Returning from this method
     * means the message is committed and will go when it is allowed to, not that it has gone.
     */
    Mono<Void> enqueueForStage(ProcessorAccess access, Ticket ticket, ProductMessageConfig config);
}
