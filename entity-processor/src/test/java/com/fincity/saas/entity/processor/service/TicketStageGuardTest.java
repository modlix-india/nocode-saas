package com.fincity.saas.entity.processor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/**
 * The guard on stage and status in the generic ticket update path.
 *
 * <p>Both fields used to be a plain assignment onto the row read back from the database, so a
 * payload could put any stage id at all on a deal and it was written and answered 200. Confirmed on
 * 2026-09-04 by PUTting stage 3484 - another tenant's stage, on another product template - onto a
 * deal and watching it persist. Omitting the field entirely wiped the stage to null, which drops the
 * deal out of every board.
 *
 * <p>Exercised through {@code applyStageStatus} rather than through the whole update, because the
 * surrounding {@code updatableEntity} needs a security context, a DAO read and the expiry rules, and
 * none of those has anything to say about whether a stage belongs to a deal.
 */
class TicketStageGuardTest {

    private static final ULong TICKET_PRODUCT = ULong.valueOf(428);
    private static final ULong TEMPLATE = ULong.valueOf(385);

    private static final ULong STAGE_OLD = ULong.valueOf(3154);
    private static final ULong STATUS_OLD = ULong.valueOf(3155);
    private static final ULong STAGE_NEW = ULong.valueOf(3156);
    private static final ULong STATUS_NEW = ULong.valueOf(3157);

    /** Belongs to client NITEE6 on product template 405. The id that used to be accepted. */
    private static final ULong STAGE_FOREIGN = ULong.valueOf(3484);

    private ProductService productService;
    private StageService stageService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        this.productService = mock(ProductService.class);
        this.stageService = mock(StageService.class);

        this.ticketService = new TicketService(
                null,
                this.productService,
                this.stageService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        ReflectionTestUtils.setField(this.ticketService, "msgService", messageServiceThatThrows());

        // Every product read in these tests is the deal's own product, on the template its stages
        // belong to. A test that wants the product to be wrong overrides this.
        when(this.productService.readById(any(ProcessorAccess.class), eq(TICKET_PRODUCT)))
                .thenReturn(Mono.just(product(TEMPLATE)));
    }

    // --- refusals -------------------------------------------------------------------------------

    /**
     * The bug this guard exists for. 3484 is a real stage, which is what made it dangerous: it reads
     * back fine and only looks wrong once somebody notices the deal is on a template that does not
     * contain its stage.
     */
    @Test
    @DisplayName("refuses a stage from another product template")
    void refusesForeignTemplateStage() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any())).thenReturn(Mono.empty());

        Ticket existing = existing();

        GenericException thrown = assertThrows(
                GenericException.class, () -> apply(existing, incoming(STAGE_FOREIGN, null)));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals(STAGE_OLD, existing.getStage(), "the deal must not have moved");
        assertEquals(STATUS_OLD, existing.getStatus(), "the status must not have moved");
    }

    @Test
    @DisplayName("refuses a stage id that does not exist at all")
    void refusesUnknownStage() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any())).thenReturn(Mono.empty());

        Ticket existing = existing();

        assertThrows(GenericException.class, () -> apply(existing, incoming(ULong.valueOf(99999999), null)));

        assertEquals(STAGE_OLD, existing.getStage());
    }

    /**
     * Without a template there is nothing to check a stage against, so the move is refused rather
     * than waved through. A product in this state is a configuration fault, not a deal fault, which
     * is why it answers the same way {@code updateStageStatus} does.
     */
    @Test
    @DisplayName("refuses the move when the deal's product has no template")
    void refusesWhenProductHasNoTemplate() {

        when(this.productService.readById(any(ProcessorAccess.class), eq(TICKET_PRODUCT)))
                .thenReturn(Mono.just(product(null)));

        Ticket existing = existing();

        GenericException thrown =
                assertThrows(GenericException.class, () -> apply(existing, incoming(STAGE_NEW, null)));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals(STAGE_OLD, existing.getStage());
    }

    // --- preserving what the payload does not carry ---------------------------------------------

    /**
     * A PUT that omits stage used to null it, and a deal with no stage falls out of every board. The
     * deal profile form and the kanban send different subsets of the same object, so a field one of
     * them does not manage must survive the other's save.
     */
    @Test
    @DisplayName("leaves the deal where it is when the payload carries no stage")
    void keepsStageWhenPayloadOmitsIt() {

        Ticket existing = existing();

        Ticket result = apply(existing, incoming(null, null));

        assertEquals(STAGE_OLD, result.getStage());
        assertEquals(STATUS_OLD, result.getStatus(), "status must not be nulled along with the absent stage");
        verify(this.productService, never()).readById(any(ProcessorAccess.class), any());
        verify(this.stageService, never()).getParentChild(any(), any(), any(), any());
    }

    /** An unrelated save - a tag or an email edit - must not pay for a stage lookup. */
    @Test
    @DisplayName("does not hit the database when stage and status are unchanged")
    void skipsLookupWhenNothingMoved() {

        Ticket existing = existing();

        Ticket result = apply(existing, incoming(STAGE_OLD, STATUS_OLD));

        assertEquals(STAGE_OLD, result.getStage());
        assertEquals(STATUS_OLD, result.getStatus());
        verify(this.productService, never()).readById(any(ProcessorAccess.class), any());
        verify(this.stageService, never()).getParentChild(any(), any(), any(), any());
    }

    // --- moves that must keep working -----------------------------------------------------------

    @Test
    @DisplayName("moves the deal when the stage belongs to its template")
    void movesToAValidStage() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any()))
                .thenReturn(Mono.just(Map.entry(stage(STAGE_NEW), List.of(stage(STATUS_NEW)))));

        Ticket result = apply(existing(), incoming(STAGE_NEW, STATUS_NEW));

        assertEquals(STAGE_NEW, result.getStage());
        assertEquals(STATUS_NEW, result.getStatus());
    }

    /**
     * The kanban drag-drop case, and the reason a mismatched status is dropped rather than refused.
     * {@code dropFunction} sets only {@code stage.id} on the card it is holding and posts the rest of
     * the deal back unchanged, so the status riding along belongs to the column the card just left.
     * Refusing that would make dragging a card fail.
     */
    @Test
    @DisplayName("drops a status that does not belong to the new stage instead of refusing the move")
    void dropsStaleStatusOnAMove() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any()))
                .thenReturn(Mono.just(Map.entry(stage(STAGE_NEW), List.of())));

        Ticket result = apply(existing(), incoming(STAGE_NEW, STATUS_OLD));

        assertEquals(STAGE_NEW, result.getStage());
        assertNull(result.getStatus(), "a status from the previous stage must not survive the move");
    }

    /** A stage with no statuses under it is ordinary, and lands a null status rather than failing. */
    @Test
    @DisplayName("accepts a move to a stage that has no statuses")
    void movesToAStageWithoutStatuses() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any()))
                .thenReturn(Mono.just(Map.entry(stage(STAGE_NEW), List.of())));

        Ticket result = apply(existing(), incoming(STAGE_NEW, null));

        assertEquals(STAGE_NEW, result.getStage());
        assertNull(result.getStatus());
    }

    /**
     * Changing only the status still has to be checked. The stage is unchanged, so an unchecked path
     * here would let any id at all into the status column.
     */
    @Test
    @DisplayName("validates a status-only change")
    void validatesStatusOnlyChange() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any()))
                .thenReturn(Mono.just(Map.entry(stage(STAGE_OLD), List.of(stage(STATUS_NEW)))));

        Ticket result = apply(existing(), incoming(STAGE_OLD, STATUS_NEW));

        assertEquals(STAGE_OLD, result.getStage());
        assertEquals(STATUS_NEW, result.getStatus());
        verify(this.stageService).getParentChild(any(), eq(TEMPLATE), any(), any());
    }

    /**
     * The stage is checked against the deal's stored product, never the payload's. Product is not
     * updatable on this path, so a payload naming a different one is either noise or an attempt to
     * change it, and checking a stage against a product the deal is not on would validate the wrong
     * pairing.
     */
    @Test
    @DisplayName("validates against the deal's product, not the payload's")
    void ignoresProductIdInThePayload() {

        when(this.stageService.getParentChild(any(), eq(TEMPLATE), any(), any()))
                .thenReturn(Mono.just(Map.entry(stage(STAGE_NEW), List.of())));

        Ticket incoming = incoming(STAGE_NEW, null).setProductId(ULong.valueOf(427));

        Ticket result = apply(existing(), incoming);

        assertEquals(STAGE_NEW, result.getStage());
        verify(this.productService).readById(any(ProcessorAccess.class), eq(TICKET_PRODUCT));
        verify(this.productService, never()).readById(any(ProcessorAccess.class), eq(ULong.valueOf(427)));
    }

    // --- helpers --------------------------------------------------------------------------------

    private Ticket apply(Ticket existing, Ticket incoming) {
        Mono<Ticket> result = ReflectionTestUtils.invokeMethod(
                this.ticketService, "applyStageStatus", access(), existing, incoming);
        assertNotNull(result);
        return result.block();
    }

    private static Ticket existing() {
        return (Ticket) new Ticket()
                .setProductId(TICKET_PRODUCT)
                .setStage(STAGE_OLD)
                .setStatus(STATUS_OLD)
                .setId(ULong.valueOf(3434));
    }

    private static Ticket incoming(ULong stage, ULong status) {
        return new Ticket().setStage(stage).setStatus(status);
    }

    private static Stage stage(ULong id) {
        return (Stage) new Stage().setId(id);
    }

    private static Product product(ULong templateId) {
        return new Product().setProductTemplateId(templateId);
    }

    private static ProcessorAccess access() {
        return ProcessorAccess.of("leadzump", "FIN", Boolean.TRUE, null, null);
    }

    /**
     * Stands in for the message service, which otherwise needs a resource bundle. The identity of the
     * exception is all these tests read, so the message id is passed straight through as its text.
     */
    private static ProcessorMessageResourceService messageServiceThatThrows() {

        ProcessorMessageResourceService msgService = mock(ProcessorMessageResourceService.class);

        when(msgService.throwMessage(any(Function.class), any(String.class), any(Object[].class)))
                .thenAnswer(TicketStageGuardTest::asError);
        when(msgService.throwMessage(any(Function.class), any(String.class))).thenAnswer(TicketStageGuardTest::asError);

        return msgService;
    }

    @SuppressWarnings("unchecked")
    private static Mono<Object> asError(InvocationOnMock invocation) {
        Function<String, GenericException> factory = invocation.getArgument(0);
        String messageId = invocation.getArgument(1);
        return Mono.error(factory.apply(messageId));
    }
}
