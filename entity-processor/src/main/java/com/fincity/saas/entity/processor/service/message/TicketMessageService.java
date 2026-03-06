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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketMessageService {

    private final ProductMessageConfigService configService;
    private final Map<MessageChannelType, TicketChannelMessageService> channelServiceMap;

    public TicketMessageService(
            ProductMessageConfigService configService, List<TicketChannelMessageService> channelServices) {
        this.configService = configService;
        this.channelServiceMap = new EnumMap<>(MessageChannelType.class);
        channelServices.forEach(svc -> this.channelServiceMap.put(svc.getChannel(), svc));
    }

    public Mono<Void> sendOnTicketCreate(ProcessorAccess access, Ticket ticket) {

        if (ticket.getProductId() == null || ticket.getStage() == null || ticket.getStatus() == null)
            return Mono.empty();

        MessageChannelType channel = MessageChannelType.WHATS_APP_TEMPLATE;

        return this.configService
                .getConfigs(access, ticket.getProductId(), ticket.getStage(), ticket.getStatus(), channel)
                .flatMapMany(Flux::fromIterable)
                .flatMap(cfg -> this.dispatch(access, ticket, cfg))
                .then()
                .onErrorResume(e -> Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketMessageService.sendOnTicketCreate"));
    }

    private Mono<Void> dispatch(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {
        TicketChannelMessageService svc = this.channelServiceMap.get(config.getChannel());
        if (svc == null) return Mono.empty();
        return svc.sendOnTicketCreate(access, ticket, config);
    }
}
