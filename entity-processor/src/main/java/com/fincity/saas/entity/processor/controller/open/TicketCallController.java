package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.service.TicketCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/open/call")
public class TicketCallController {

    private TicketCallService ticketCallService;

    @Autowired
    private void setTicketCallService(TicketCallService ticketCallService) {
        this.ticketCallService = ticketCallService;
    }

    @GetMapping()
    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            ServerHttpRequest request) {
        return ticketCallService.incomingExotelCall(appCode, clientCode, request);
    }
}
