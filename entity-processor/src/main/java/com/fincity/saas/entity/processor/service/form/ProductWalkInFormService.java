package com.fincity.saas.entity.processor.service.form;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.form.ProductWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.form.ProductWalkInForm;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormTicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.model.response.WalkInFormResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ProductWalkInFormService
        extends BaseWalkInFormService<
                EntityProcessorProductWalkInFormsRecord, ProductWalkInForm, ProductWalkInFormDAO> {

    private static final String SYSTEM = "SYSTEM";
    private static final String PRODUCT_WALK_IN_FORM_CACHE = "productWalkInForm";
    private final ProductService productService;
    private TicketService ticketService;

    private ProductTemplateWalkInFormService productTemplateWalkInFormService;

    public ProductWalkInFormService(ProductService productService) {
        this.productService = productService;
    }

    @Autowired
    private void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Lazy
    @Autowired
    private void setProductTemplateWalkInFormService(
            ProductTemplateWalkInFormService productTemplateWalkInFormService) {
        this.productTemplateWalkInFormService = productTemplateWalkInFormService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_WALK_IN_FORM_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_WALK_IN_FORMS;
    }

    @Override
    protected String getProductEntityName() {
        return productService.getEntityName();
    }

    @Override
    protected Mono<Tuple2<ULong, ULong>> resolveProduct(ProcessorAccess access, Identity productId) {
        return productService
                .readIdentityWithAccess(access, productId)
                .map(product -> Tuples.of(product.getId(), product.getProductTemplateId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.resolveProduct"));
    }

    @Override
    protected ProductWalkInForm create(
            String name, ULong entityId, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        return (ProductWalkInForm) new ProductWalkInForm()
                .setName(name)
                .setProductId(entityId)
                .setStageId(stageId)
                .setStatusId(statusId)
                .setAssignmentType(assignmentType);
    }

    public Mono<List<IdAndValue<BigInteger, String>>> getWalkInFromUsers(String appCode, String clientCode) {

        if (clientCode == null || clientCode.equals(SYSTEM)) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> super.securityService.getClientByCode(clientCode),
                        client -> super.securityService
                                .hasReadAccess(appCode, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (client, hasAccess) ->
                                super.securityService.getClientUserInternal(List.of(client.getId()), null),
                        (client, hasAccess, users) -> Mono.just(users.stream()
                                .filter(user -> user.getFirstName() != null && user.getLastName() != null)
                                .map(user -> IdAndValue.of(
                                        user.getId(),
                                        NameUtil.assembleFullName(
                                                user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                .sorted(Comparator.comparing(IdAndValue::getId))
                                .toList()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.getWalkInFromUsers"));
    }

    public Mono<Ticket> getWalkInTicket(
            String appCode, String clientCode, Identity productId, PhoneNumber phoneNumber) {

        if (clientCode == null || clientCode.equals(SYSTEM)) return Mono.empty();

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);

        return this.resolveProduct(access, productId)
                .flatMap(product -> this.ticketService.getTicket(access, product.getT1(), phoneNumber, null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.getWalkInTicket"));
    }

    public Mono<Product> getWalkInProduct(String appCode, String clientCode, Identity productId) {

        if (clientCode == null || clientCode.equals(SYSTEM)) return Mono.empty();

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);

        return this.productService
                .readIdentityWithAccess(access, productId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.getWalkInProduct"));
    }

    public Mono<ProcessorResponse> createWalkInTicket(
            String appCode, String clientCode, Identity productId, WalkInFormTicketRequest ticketRequest) {

        if (clientCode == null || clientCode.equals(SYSTEM)) return Mono.empty();

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.getWalkInFormResponseInternal(access, productId),
                        walkInFormResponse -> this.validateAndGetTicket(access, walkInFormResponse, ticketRequest),
                        (walkInFormResponse, ticket) -> ticket.getId() != null
                                ? this.updateExistingTicket(access, ticket, walkInFormResponse, ticketRequest)
                                : this.createNewTicket(access, ticket, walkInFormResponse, ticketRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.createWalkInTicket"));
    }

    private Mono<Ticket> validateAndGetTicket(
            ProcessorAccess access, WalkInFormResponse walkInFormResponse, WalkInFormTicketRequest ticketRequest) {

        if (walkInFormResponse.getAssignmentType().equals(AssignmentType.MANUAL) && ticketRequest.getUserId() == null)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Owner User");

        return this.ticketService
                .getTicket(access, walkInFormResponse.getProductId(), ticketRequest.getPhoneNumber(), null)
                .switchIfEmpty(Mono.just(Ticket.of(ticketRequest)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.validateAndGetTicket"));
    }

    private Mono<ProcessorResponse> updateExistingTicket(
            ProcessorAccess access,
            Ticket ticket,
            WalkInFormResponse walkInFormResponse,
            WalkInFormTicketRequest ticketRequest) {

        if (walkInFormResponse.getAssignmentType().equals(AssignmentType.DEAL_FLOW)) ticketRequest.setUserId(null);

        if (ticketRequest.getEmail() != null)
            ticket.setEmail(ticketRequest.getEmail().getAddress());

        if (ticketRequest.getName() != null) ticket.setName(ticketRequest.getName());

        if (ticketRequest.getDescription() != null) ticket.setDescription(ticketRequest.getDescription());

        if (ticket.getSubSource() == null) ticket.setSubSource(ticketRequest.getSubSource());

        return this.ticketService
                .updateTicketStage(
                        access,
                        ticket,
                        ticketRequest.getUserId(),
                        walkInFormResponse.getStageId(),
                        walkInFormResponse.getStatusId(),
                        null,
                        ticketRequest.getComment())
                .map(updated -> ProcessorResponse.ofNoContent(updated.getCode(), updated.getEntitySeries()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.updateExistingTicket"));
    }

    private Mono<ProcessorResponse> createNewTicket(
            ProcessorAccess access,
            Ticket ticket,
            WalkInFormResponse walkInFormResponse,
            WalkInFormTicketRequest ticketRequest) {

        if (walkInFormResponse.getAssignmentType().equals(AssignmentType.DEAL_FLOW)) ticketRequest.setUserId(null);

        return this.ticketService
                .create(
                        access,
                        ticket.setStage(walkInFormResponse.getStageId())
                                .setStatus(walkInFormResponse.getStatusId())
                                .setSource("Walk In")
                                .setProductId(walkInFormResponse.getProductId())
                                .setAssignedUserId(ticketRequest.getUserId()))
                .map(created -> ProcessorResponse.ofCreated(created.getCode(), created.getEntitySeries()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.createNewTicket"));
    }

    public Mono<WalkInFormResponse> getWalkInFormResponse(String appCode, String clientCode, Identity productId) {

        if (clientCode == null || clientCode.equals(SYSTEM)) return Mono.empty();

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);

        return this.getWalkInFormResponseInternal(access, productId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.getWalkInFormResponse"));
    }

    private Mono<WalkInFormResponse> getWalkInFormResponseInternal(ProcessorAccess access, Identity productId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.resolveProduct(access, productId),
                        product -> this.getWalkInFormResponse(access, product.getT1())
                                .switchIfEmpty(this.productTemplateWalkInFormService.getWalkInFormResponse(
                                        access, product.getT2())))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.getWalkInFormResponseInternal"));
    }
}
