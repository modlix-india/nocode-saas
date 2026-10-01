package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.service.TicketCallService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
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

    /** TeleCMI's inbound HTTP flow form post, answered with whom to ring; the webhook token is {@code ?t=}. */
    @PostMapping(value = "/telecmi", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<Map<String, Object>> incomingTelecmiCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestParam(name = "t", required = false) String token,
            ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(MultiValueMap::toSingleValueMap)
                .flatMap(flow -> ticketCallService.incomingTelecmiCall(appCode, clientCode, token, flow));
    }
}
