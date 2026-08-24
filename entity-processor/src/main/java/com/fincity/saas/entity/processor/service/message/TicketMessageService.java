package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.product.ProductMessageConfigService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketMessageService {

    private static final Logger logger = LoggerFactory.getLogger(TicketMessageService.class);

    private final ProductMessageConfigService configService;
    private final Map<MessageChannelType, TicketChannelMessageService> channelServiceMap;

    public TicketMessageService(
            ProductMessageConfigService configService, List<TicketChannelMessageService> channelServices) {
        this.configService = configService;
        this.channelServiceMap = new EnumMap<>(MessageChannelType.class);
        channelServices.forEach(svc -> this.channelServiceMap.put(svc.getChannel(), svc));
    }

    /**
     * Queues whatever the rules say a deal should receive at the stage it is now on.
     *
     * <p>Called both when a deal is created and whenever it moves, because those are the same event
     * as far as the rules are concerned: a deal has arrived somewhere and something may be owed. The
     * "did we already send this" question is settled per rule per deal further down, in the channel
     * service, where the outbox can actually be consulted. Answering it here would mean either
     * re-sending a welcome pack every time a deal bounced between two stages, or never firing a
     * later stage's rule at all.
     */
    public Mono<Void> enqueueForStage(ProcessorAccess access, Ticket ticket) {

        if (ticket.getProductId() == null || ticket.getStage() == null || ticket.getStatus() == null)
            return Mono.empty();

        MessageChannelType channel = MessageChannelType.WHATS_APP_TEMPLATE;

        return this.configService
                .getConfigs(access, ticket.getProductId(), ticket.getStage(), ticket.getStatus(), channel)
                .flatMapMany(Flux::fromIterable)
                // Sequentially, so a packet's rows are created in rule order. The sequence is what
                // the sweeper drains in, and what "an earlier message in this packet failed" is
                // judged against, so getting it right here is not cosmetic.
                .concatMap(cfg -> this.dispatch(access, ticket, cfg))
                .then()
                // Messaging must never fail the stage change or the ticket creation that triggered
                // it, but a swallowed error is indistinguishable from "no rules matched", so log
                // before dropping it.
                .onErrorResume(e -> {
                    logger.error(
                            "Failed to queue stage messages for ticket {} on product {} at stage {}",
                            ticket.getId(),
                            ticket.getProductId(),
                            ticket.getStage(),
                            e);
                    return Mono.empty();
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketMessageService.enqueueForStage"));
    }

    private Mono<Void> dispatch(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {
        TicketChannelMessageService svc = this.channelServiceMap.get(config.getChannel());
        if (svc == null) return Mono.empty();
        return svc.enqueueForStage(access, ticket, config);
    }
}
