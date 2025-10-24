package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dto.ProductComm;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TicketCallService {

    private static final ConnectionType CALL_CONNECTION = ConnectionType.CALL;

    private final TicketService ticketService;

    private final ProductCommService productCommService;

    private final IFeignMessageService messageService;

    private final ProcessorMessageResourceService msgService;

    public TicketCallService(
            TicketService ticketService,
            ProductCommService productCommService,
            IFeignMessageService messageService,
            ProcessorMessageResourceService msgService) {
        this.ticketService = ticketService;
        this.productCommService = productCommService;
        this.messageService = messageService;
        this.msgService = msgService;
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, ServerHttpRequest request) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(request.getQueryParams());

        PhoneNumber from = PhoneNumber.of(exotelRequest.getFrom());

        PhoneNumber callerId = PhoneNumber.of(exotelRequest.getTo());

        return FlatMapUtil.flatMapMono(
                () -> productCommService
                        .getByPhoneNumber(access, CALL_CONNECTION, ConnectionSubType.EXOTEL, callerId)
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.UNKNOWN_EXOTEL_CALLER_ID,
                                callerId.getNumber())),
                productComm -> ticketService
                        .getTicket(access, productComm.getProductId(), from, null)
                        .switchIfEmpty(this.createExotelTicket(access, from, productComm)),
                (productComm, ticket) ->
                        messageService.connectCall(appCode, clientCode, (IncomingCallRequest) new IncomingCallRequest()
                                .setProviderIncomingRequest(
                                        request.getQueryParams().toSingleValueMap())
                                .setConnectionName(productComm.getConnectionName())
                                .setUserId(ticket.getAssignedUserId())));
    }

    private Mono<Ticket> createExotelTicket(ProcessorAccess access, PhoneNumber from, ProductComm productComm) {

        ULong productId = productComm.getProductId();

        String source = productComm.getSource() != null ? productComm.getSource() : SourceUtil.DEFAULT_CALL_SOURCE;

        String subSource =
                productComm.getSubSource() != null ? productComm.getSubSource() : SourceUtil.DEFAULT_CALL_SUB_SOURCE;

        return ticketService.createInternal(
                access,
                new Ticket()
                        .setName("New Customer")
                        .setDialCode(from.getCountryCode())
                        .setPhoneNumber(from.getNumber())
                        .setProductId(productId)
                        .setSource(source)
                        .setSubSource(subSource));
    }
}
