package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractServiceFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketCallService implements IRepositoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(TicketCallService.class);
    private static final ConnectionType CALL_CONNECTION = ConnectionType.CALL;
    private static final String NAMESPACE = "EntityProcessor.TicketCall";
    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final TicketService ticketService;
    private final ProductCommService productCommService;
    private final IFeignMessageService messageService;
    private final ProcessorMessageResourceService msgService;
    private final ActivityService activityService;
    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    public TicketCallService(
            TicketService ticketService,
            ProductCommService productCommService,
            IFeignMessageService messageService,
            ProcessorMessageResourceService msgService,
            ActivityService activityService,
            Gson gson) {
        this.ticketService = ticketService;
        this.productCommService = productCommService;
        this.messageService = messageService;
        this.msgService = msgService;
        this.activityService = activityService;
        this.gson = gson;
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, ServerHttpRequest request) {
        logger.info(
                "Received incoming Exotel call request - appCode: {}, clientCode: {}, queryParams: {}",
                appCode,
                clientCode,
                request.getQueryParams());
        return this.incomingExotelCall(
                        appCode, clientCode, request.getQueryParams().toSingleValueMap())
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketCallService.incomingExotelCall[ServerHttpRequest]"));
    }

    public Mono<ExotelConnectAppletResponse> incomingExotelCall(
            String appCode, String clientCode, Map<String, String> providerIncomingRequest) {

        logger.info(
                "Processing incoming Exotel call - appCode: {}, clientCode: {}, providerRequest: {}",
                appCode,
                clientCode,
                providerIncomingRequest);

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (providerIncomingRequest != null) {
            providerIncomingRequest.forEach((k, v) -> params.put(k, List.of(v)));
        }

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(params);

        PhoneNumber from = PhoneNumber.of(exotelRequest.getFrom());
        PhoneNumber callerId = PhoneNumber.of(exotelRequest.getTo());

        logger.debug("Parsed phone numbers - from: {}, callerId: {}", from, callerId);

        return FlatMapUtil.flatMapMono(
                        () -> productCommService
                                .getByPhoneNumber(access, CALL_CONNECTION, ConnectionSubType.EXOTEL, callerId)
                                .doOnNext(productComm -> logger.info(
                                        "Found ProductComm for callerId: {}, productId: {}, connectionName: {}",
                                        callerId.getNumber(),
                                        productComm.getProductId(),
                                        productComm.getConnectionName()))
                                .doOnError(error -> logger.error(
                                        "Error fetching ProductComm for callerId: {}", callerId.getNumber(), error))
                                .switchIfEmpty(Mono.defer(() -> {
                                    logger.warn("No ProductComm found for callerId: {}", callerId.getNumber());
                                    return this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            ProcessorMessageResourceService.UNKNOWN_EXOTEL_CALLER_ID,
                                            callerId.getNumber());
                                })),
                        productComm -> ticketService
                                .getTicket(access, productComm.getProductId(), from, null)
                                .doOnNext(ticket -> logger.info(
                                        "Found existing ticket - ticketId: {}, productId: {}, assignedUserId: {}",
                                        ticket.getId(),
                                        ticket.getProductId(),
                                        ticket.getAssignedUserId()))
                                .doOnError(error -> logger.error(
                                        "Error fetching ticket for productId: {}, from: {}",
                                        productComm.getProductId(),
                                        from.getNumber(),
                                        error))
                                .switchIfEmpty(Mono.defer(() -> {
                                    logger.info(
                                            "No existing ticket found, creating new ticket for productId: {}, from: {}",
                                            productComm.getProductId(),
                                            from.getNumber());
                                    return this.createExotelTicket(access, from, productComm)
                                            .doOnNext(ticket -> logger.info(
                                                    "Created new ticket - ticketId: {}, productId: {}",
                                                    ticket.getId(),
                                                    ticket.getProductId()))
                                            .doOnError(error -> logger.error("Error creating new ticket", error));
                                })),
                        (productComm, ticket) -> {
                            logger.info(
                                    "Connecting call - ticketId: {}, connectionName: {}, assignedUserId: {}",
                                    ticket.getId(),
                                    productComm.getConnectionName(),
                                    ticket.getAssignedUserId());
                            return messageService
                                    .connectCall(appCode, clientCode, (IncomingCallRequest) new IncomingCallRequest()
                                            .setProviderIncomingRequest(providerIncomingRequest)
                                            .setConnectionName(productComm.getConnectionName())
                                            .setUserId(ticket.getAssignedUserId()))
                                    .doOnNext(response ->
                                            logger.info("Call connected successfully - ticketId: {}", ticket.getId()))
                                    .doOnError(error -> logger.error(
                                            "Error connecting call for ticketId: {}", ticket.getId(), error));
                        },
                        (productComm, ticket, response) -> {
                            logger.info("Logging call activity - ticketId: {}", ticket.getId());
                            return this.logCall(access, ticket)
                                    .doOnSuccess(v -> logger.debug(
                                            "Call activity logged successfully - ticketId: {}", ticket.getId()))
                                    .doOnError(error -> logger.error(
                                            "Error logging call activity for ticketId: {}", ticket.getId(), error))
                                    .thenReturn(response);
                        })
                .doOnSuccess(response -> logger.info(
                        "Successfully processed incoming Exotel call - appCode: {}, clientCode: {}",
                        appCode,
                        clientCode))
                .doOnError(error -> logger.error(
                        "Failed to process incoming Exotel call - appCode: {}, clientCode: {}",
                        appCode,
                        clientCode,
                        error))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallService.incomingExotelCall"));
    }

    private Mono<Ticket> createExotelTicket(ProcessorAccess access, PhoneNumber from, ProductComm productComm) {

        logger.debug(
                "Creating Exotel ticket - productId: {}, from: {}, source: {}, subSource: {}",
                productComm.getProductId(),
                from.getNumber(),
                productComm.getSource(),
                productComm.getSubSource());

        ULong productId = productComm.getProductId();

        String source = productComm.getSource() != null ? productComm.getSource() : SourceUtil.DEFAULT_CALL_SOURCE;

        String subSource =
                productComm.getSubSource() != null ? productComm.getSubSource() : SourceUtil.DEFAULT_CALL_SUB_SOURCE;

        return ticketService
                .create(
                        access,
                        new Ticket()
                                .setName("New Customer")
                                .setDialCode(from.getCountryCode())
                                .setPhoneNumber(from.getNumber())
                                .setProductId(productId)
                                .setSource(source)
                                .setSubSource(subSource))
                .doOnSuccess(ticket -> logger.info(
                        "Exotel ticket created successfully - ticketId: {}, productId: {}, phoneNumber: {}",
                        ticket.getId(),
                        productId,
                        from.getNumber()))
                .doOnError(error -> logger.error(
                        "Failed to create Exotel ticket - productId: {}, phoneNumber: {}",
                        productId,
                        from.getNumber(),
                        error));
    }

    private Mono<Void> logCall(ProcessorAccess access, Ticket ticket) {
        logger.debug("Logging call activity - ticketId: {}", ticket.getId());
        return activityService
                .acCallLog(access, ticket, null)
                .doOnSuccess(v -> logger.debug("Call activity logged - ticketId: {}", ticket.getId()))
                .doOnError(error -> logger.error("Error logging call activity - ticketId: {}", ticket.getId(), error));
    }

    @PostConstruct
    private void init() {
        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
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
