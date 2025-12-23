package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletRequest;
import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@Service
public class TicketCallService implements IRepositoryProvider {

    private static final ConnectionType CALL_CONNECTION = ConnectionType.CALL;

    private final TicketService ticketService;

    private final ProductCommService productCommService;

    private final IFeignMessageService messageService;

    private final ProcessorMessageResourceService msgService;

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    public TicketCallService(
            TicketService ticketService,
            ProductCommService productCommService,
            IFeignMessageService messageService,
            ProcessorMessageResourceService msgService,
            Gson gson) {
        this.ticketService = ticketService;
        this.productCommService = productCommService;
        this.messageService = messageService;
        this.msgService = msgService;
        this.gson = gson;
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, ServerHttpRequest request) {
        return this.incomingExotelCall(
                appCode, clientCode, request.getQueryParams().toSingleValueMap());
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, Map<String, String> providerIncomingRequest) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (providerIncomingRequest != null) {
            providerIncomingRequest.forEach((k, v) -> params.put(k, List.of(v)));
        }

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(params);

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
                                .setProviderIncomingRequest(providerIncomingRequest)
                                .setConnectionName(productComm.getConnectionName())
                                .setUserId(ticket.getAssignedUserId())));
    }

    private Mono<Ticket> createExotelTicket(ProcessorAccess access, PhoneNumber from, ProductComm productComm) {

        ULong productId = productComm.getProductId();

        String source = productComm.getSource() != null ? productComm.getSource() : SourceUtil.DEFAULT_CALL_SOURCE;

        String subSource =
                productComm.getSubSource() != null ? productComm.getSubSource() : SourceUtil.DEFAULT_CALL_SUB_SOURCE;

        return ticketService.create(
                access,
                new Ticket()
                        .setName("New Customer")
                        .setDialCode(from.getCountryCode())
                        .setPhoneNumber(from.getNumber())
                        .setProductId(productId)
                        .setSource(source)
                        .setSubSource(subSource));
    }

    @PostConstruct
    private void init() {
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "TicketCall",
                "IncomingExotelCall",
                ClassSchema.ArgSpec.string("appCode"),
                ClassSchema.ArgSpec.string("clientCode"),
                ClassSchema.ArgSpec.stringMap("providerIncomingRequest"),
                "result",
                Schema.ofRef("EntityProcessor.Common.ExotelConnectAppletResponse"),
                gson,
                this::incomingExotelCall));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ExotelConnectAppletResponse.class, classSchema);
    }
}
