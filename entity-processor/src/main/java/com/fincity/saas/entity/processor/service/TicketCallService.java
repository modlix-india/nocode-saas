package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletRequest;
import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import org.jooq.types.ULong;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TicketCallService {

    private static final ConnectionType CALL_CONNECTION = ConnectionType.CALL;

    private final TicketService ticketService;

    private final ProductCommService productCommService;

    private final IFeignMessageService messageService;

    public TicketCallService(
            TicketService ticketService, ProductCommService productCommService, IFeignMessageService messageService) {
        this.ticketService = ticketService;
        this.productCommService = productCommService;
        this.messageService = messageService;
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, ServerHttpRequest request) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(request.getQueryParams());

        PhoneNumber from = PhoneNumber.of(exotelRequest.getFrom());

        PhoneNumber callerId = PhoneNumber.of(exotelRequest.getTo());

        return FlatMapUtil.flatMapMono(
                        () -> ticketService.readByPhoneNumber(access, from),
                        ticket -> productCommService.getByPhoneNumber(
                                access, CALL_CONNECTION, ConnectionSubType.EXOTEL, callerId),
                        (ticket, productComm) -> messageService.connectCall(
                                appCode, clientCode, (IncomingCallRequest) new IncomingCallRequest()
                                        .setProviderIncomingRequest(request.getQueryParams())
                                        .setConnectionName(productComm.getConnectionName())
                                        .setUserId(ticket.getAssignedUserId())))
                .switchIfEmpty(this.incomingExotelCallNewTicket(access, from, callerId, request));
    }

    private Mono<ExotelConnectAppletResponse> incomingExotelCallNewTicket(
            ProcessorAccess access, PhoneNumber from, PhoneNumber callerId, ServerHttpRequest request) {
        return FlatMapUtil.flatMapMono(
                () -> productCommService.getByPhoneNumber(access, CALL_CONNECTION, ConnectionSubType.EXOTEL, callerId),
                productComm -> this.createExotelTicket(access, productComm.getProductId(), from),
                (productComm, ticket) -> messageService.connectCall(
                        access.getAppCode(), access.getClientCode(), (IncomingCallRequest) new IncomingCallRequest()
                                .setProviderIncomingRequest(request.getQueryParams())
                                .setConnectionName(productComm.getConnectionName())
                                .setUserId(ticket.getAssignedUserId())));
    }

    private Mono<Ticket> createExotelTicket(ProcessorAccess access, ULong productId, PhoneNumber from) {
        return ticketService.createInternal(
                access,
                new Ticket()
                        .setDialCode(from.getCountryCode())
                        .setPhoneNumber(from.getNumber())
                        .setProductId(productId)
                        .setSource(CALL_CONNECTION.name())
                        .setSubSource(ConnectionSubType.EXOTEL.name()));
    }
}
