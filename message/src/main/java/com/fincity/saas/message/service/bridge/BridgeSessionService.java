package com.fincity.saas.message.service.bridge;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.bridge.BridgeInstanceDAO;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.bridge.BridgeInstance;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.bridge.BridgeSessionSnapshot;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Everything the platform does <em>to</em> a bridge session: create it, watch it pair, send through
 * it, unlink it.
 *
 * <p>The counterpart to {@link BridgeRegistryService}, which handles what the fleet reports back.
 * Together they are the whole of the message service's role as control plane.
 */
@Service
public class BridgeSessionService {

    private static final Logger logger = LoggerFactory.getLogger(BridgeSessionService.class);

    private final WhatsappPhoneNumberDAO sessionDao;
    private final BridgeInstanceDAO instanceDao;
    private final BridgePlacementService placementService;
    private final BridgeClient bridgeClient;

    /**
     * Which service owns conversations on sessions created through this path.
     *
     * <p>Configurable rather than hard-coded so a second consumer does not require a code change,
     * but defaulted, because today there is exactly one and a null owner parks every inbound message
     * instead of delivering it.
     */
    @Value("${message.bridge.default-owner-service:entity-processor}")
    private String defaultOwnerService;

    public BridgeSessionService(
            WhatsappPhoneNumberDAO sessionDao,
            BridgeInstanceDAO instanceDao,
            BridgePlacementService placementService,
            BridgeClient bridgeClient) {
        this.sessionDao = sessionDao;
        this.instanceDao = instanceDao;
        this.placementService = placementService;
        this.bridgeClient = bridgeClient;
    }

    /**
     * Creates a session and asks its instance to start pairing.
     *
     * <p>Order matters and is not arbitrary. The row is written first so the session id exists
     * before anything is told about it, then the assignment is claimed, then the bridge is called.
     * Calling the bridge first would mean an instance holding a session this service has no record
     * of, which is precisely the stray that reconciliation exists to catch and that nobody wants to
     * create deliberately.
     */
    /**
     * Starts linking a number.
     *
     * <p>Refuses up front when the number already has a placed session, rather than letting it reach
     * the unique key on the generated linked-number column. The constraint is correct and stays; the
     * point is that a customer clicking Link twice is an ordinary thing to do and deserves a sentence
     * instead of an integrity violation, whose text used to end up on screen.
     */
    public Mono<BridgeSessionSnapshot> createSession(
            MessageAccess access, String phone, ULong productId, String ownerService) {

        return this.sessionDao
                .getPlacedByNumber(access.getAppCode(), access.getClientCode(), phone)
                .flatMap(existing -> Mono.<BridgeSessionSnapshot>error(new BridgeNumberAlreadyLinkedException(
                        phone, existing.getSessionState() == null ? null : existing.getSessionState().name())))
                .switchIfEmpty(Mono.defer(() -> FlatMapUtil.flatMapMono(
                        () -> this.placementService.place(phone),
                        instance -> this.createRow(access, phone, productId, ownerService, instance),
                        (instance, row) -> this.claimAndStart(access, instance, row, phone))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.createSession"));
    }

    private Mono<WhatsappPhoneNumber> createRow(
            MessageAccess access, String phone, ULong productId, String ownerService, BridgeInstance instance) {

        WhatsappPhoneNumber row = new WhatsappPhoneNumber();
        row.setAppCode(access.getAppCode());
        row.setClientCode(access.getClientCode());
        row.setDisplayPhoneNumber(phone);
        row.setProductId(productId);
        row.setOwnerService(
                ownerService == null || ownerService.isBlank() ? this.defaultOwnerService : ownerService);
        row.setCountry(this.placementService.countryOf(phone).orElse(null));

        return this.sessionDao.create(row);
    }

    /**
     * Claims the assignment and starts pairing, cleaning up if the bridge refuses.
     *
     * <p>The cleanup is load-bearing rather than tidiness. A row left holding
     * {@code BRIDGE_INSTANCE_ID} after a failed create keeps the unique key on the linked number,
     * so the customer would be unable to retry with the same number and the error they saw would be
     * about capacity while the real cause was our own abandoned row.
     */
    private Mono<BridgeSessionSnapshot> claimAndStart(
            MessageAccess access, BridgeInstance instance, WhatsappPhoneNumber row, String phone) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return this.sessionDao
                .assign(row.getCode(), instance.getInstanceId(), row.getCountry(), now)
                .flatMap(claimed -> {
                    if (claimed == 0)
                        // Somebody else took the row between the insert and here. Vanishingly
                        // unlikely on a fresh code, but the guard is what makes "one session, one
                        // instance" true rather than probable.
                        return Mono.error(new IllegalStateException(
                                "Session " + row.getCode() + " was already assigned; not starting it twice."));

                    return this.bridgeClient.createSession(
                            instance.getBaseUrl(),
                            row.getCode(),
                            access.getAppCode(),
                            access.getClientCode(),
                            phone);
                })
                .onErrorResume(e -> {
                    logger.error(
                            "Could not start session {} for {} on bridge {}. Releasing the assignment"
                                    + " so the number can be linked again.",
                            row.getCode(),
                            phone,
                            instance.getInstanceId(),
                            e);

                    // A short sentence, never the exception text. SESSION_REASON is rendered to the
                    // customer on the numbers page, and a JOOQ IntegrityConstraintViolationException
                    // carries the whole SQL statement in its message: that is how an UPDATE with its
                    // bind placeholders ended up on screen. The detail belongs in the log above,
                    // which has the stack trace and the ids to correlate on.
                    return this.sessionDao
                            .releaseAssignment(
                                    row.getCode(), WhatsappSessionState.LOGGED_OUT, "linking could not be started", now)
                            .then(Mono.error(e));
                });
    }

    /**
     * Every session a tenant has, from our own rows rather than from the instances.
     *
     * <p>Deliberately the cached view. This backs a list, where a value up to one heartbeat stale is
     * fine, and reading it live would mean fanning out to every instance the tenant has a number on
     * and failing the whole page if any one of them is down.
     */
    public Mono<List<WhatsappPhoneNumber>> listSessions(MessageAccess access) {
        return this.sessionDao
                .listForTenant(access.getAppCode(), access.getClientCode())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.listSessions"));
    }

    /**
     * The session a product's messages go out from.
     *
     * <p>Falls back to the tenant's default number when the product has none of its own, which is a
     * working configuration rather than a missing one: most tenants run a single number across
     * everything.
     */
    public Mono<WhatsappPhoneNumber> getByProduct(MessageAccess access, ULong productId) {
        return this.sessionDao
                .getPlacedByProduct(access.getAppCode(), access.getClientCode(), productId)
                .switchIfEmpty(Mono.defer(
                        () -> this.sessionDao.getPlacedDefault(access.getAppCode(), access.getClientCode())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.getByProduct"));
    }

    /**
     * The session a caller's named code resolves to, or the tenant default if it resolves to
     * nothing.
     *
     * <p>Replaces {@link #getByProduct} as the send path's entry point. The difference is who holds
     * the mapping: the product now names its own number, so the caller arrives with a code rather
     * than asking this service to look one up. Placement and the fallback stay here because they are
     * this service's to know.
     *
     * <p>A code that resolves to nothing is treated exactly like no code at all. It crosses a schema
     * boundary with no foreign key behind it, so it can name a row that has been unlinked or
     * deactivated, and the alternative to falling back is a product that silently stops sending.
     */
    public Mono<WhatsappPhoneNumber> resolve(MessageAccess access, String sessionCode) {
        return this.sessionDao
                .getPlacedByCode(access.getAppCode(), access.getClientCode(), sessionCode)
                .switchIfEmpty(Mono.defer(
                        () -> this.sessionDao.getPlacedDefault(access.getAppCode(), access.getClientCode())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.resolve"));
    }

    /**
     * Makes one number the tenant's default, and the only one.
     *
     * <p>Clears the flag across the tenant before setting it, so the two writes together leave
     * exactly one default. They are not in a transaction: the window between them is sub-millisecond
     * and the worst reachable state is no default at all, which {@code getPlacedDefault} already
     * handles by falling back to the lowest-id placed session. A transaction here would buy
     * consistency against a failure mode that degrades to the behaviour we had before this existed.
     *
     * <p>Refuses codes that name nothing placeable, rather than marking a number that cannot send.
     */
    public Mono<Boolean> markDefault(MessageAccess access, String sessionCode) {

        return this.sessionDao
                .getPlacedByCode(access.getAppCode(), access.getClientCode(), sessionCode)
                .flatMap(row -> this.sessionDao
                        .clearDefault(access.getAppCode(), access.getClientCode())
                        .then(this.sessionDao.markDefault(access.getAppCode(), access.getClientCode(), sessionCode))
                        .map(updated -> updated > 0))
                .defaultIfEmpty(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.markDefault"));
    }

    /** The current pairing code, polled by the link panel while the session is PAIRING. */
    public Mono<Map<String, Object>> getQr(MessageAccess access, String sessionId) {
        return this.withInstance(access, sessionId, (row, instance) ->
                        this.bridgeClient.getQr(instance.getBaseUrl(), sessionId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.getQr"));
    }

    /**
     * Live state, read from the instance rather than from our own row.
     *
     * <p>Our row is the last thing a heartbeat reported, which can be up to fifteen seconds stale.
     * That is fine for a list and wrong for the panel somebody is staring at while scanning a QR
     * code.
     */
    public Mono<BridgeSessionSnapshot> getSession(MessageAccess access, String sessionId) {
        return this.withInstance(access, sessionId, (row, instance) ->
                        this.bridgeClient.getSession(instance.getBaseUrl(), sessionId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.getSession"));
    }

    /**
     * Unlinks a number and releases its slot.
     *
     * <p>The assignment is released even if the bridge call fails. An instance that cannot be
     * reached is not a reason to leave the customer permanently unable to re-link: the device store
     * is wiped by the bridge when it next sees the session gone, and reconciliation reports the
     * mismatch in the meantime.
     */
    /**
     * Unlinks a number, whether or not any instance currently holds it.
     *
     * <p>Deliberately not routed through {@link #withInstance}. That helper refuses a session with
     * no instance assigned, which is right for sending and wrong here: unlinking a session nobody
     * holds is trivially satisfiable, because there is no process to tell. Refusing was the one
     * failure that left a row the customer could not get rid of by any means in the UI.
     *
     * <p>The rows that hit this are real: pre-pivot Cloud API numbers carry a phone-number id and no
     * bridge instance at all, and cutover leaves them in the list needing removal. So does any
     * session whose instance was decommissioned.
     *
     * <p>The release happens regardless of what the bridge says. A local row still pointing at a
     * session the customer has already unlinked is worse than a bridge that finds out late, and
     * reconciliation reports the difference until it catches up.
     */
    public Mono<Void> unlink(MessageAccess access, String sessionId) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return this.withRow(access, sessionId)
                .flatMap(row -> {
                    if (row.getBridgeInstanceId() == null) {
                        logger.info(
                                "Unlinking session {}, which no instance holds. Releasing the row"
                                        + " locally; there is nothing to tell.",
                                sessionId);
                        return Mono.empty();
                    }

                    return this.instanceDao
                            .findByInstanceId(row.getBridgeInstanceId())
                            .flatMap(instance -> this.bridgeClient
                                    .unlink(instance.getBaseUrl(), sessionId)
                                    .onErrorResume(e -> {
                                        logger.error(
                                                "Bridge {} could not unlink session {}. Releasing the"
                                                        + " assignment anyway; reconciliation will report"
                                                        + " it until the instance catches up.",
                                                instance.getInstanceId(),
                                                sessionId,
                                                e);
                                        return Mono.empty();
                                    }))
                            .onErrorResume(e -> {
                                logger.error(
                                        "Could not reach the instance holding session {}. Releasing the"
                                                + " assignment anyway.",
                                        sessionId,
                                        e);
                                return Mono.empty();
                            });
                })
                .then(this.sessionDao.releaseAssignment(
                        sessionId, WhatsappSessionState.LOGGED_OUT, "unlinked by the customer", now))
                // And take it off the tenant's list. Releasing the assignment alone left the row
                // showing with a new state, which reads as the unlink having done nothing.
                .then(this.sessionDao.deactivate(sessionId))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.unlink"));
    }

    /**
     * Sends a text through a session.
     *
     * <p>This is Layer 1 only. The 24-hour rule, the caps and the warm-up all live in
     * entity-processor, where the message history that decides them is stored; by the time a send
     * reaches here it has already been allowed. What still happens on the bridge is the randomised
     * 5-15 second gap, the typing indicator and the hourly ceiling, which apply to every send
     * including one a person typed, and which are why this call can block for most of a minute.
     */
    /**
     * Sends an attachment, with an optional caption.
     *
     * <p>Same sendability gate as text. A session that cannot send text cannot send a photo either,
     * and letting media through a gate text does not pass would put traffic on a number precisely
     * when it is least able to take it.
     */
    public Mono<Map<String, Object>> sendMedia(
            MessageAccess access,
            String sessionId,
            String to,
            String filePath,
            String kind,
            String mimeType,
            String fileName,
            String caption,
            boolean voiceNote,
            String resourceType) {

        return this.withInstance(access, sessionId, (row, instance) -> {
                    if (row.getSessionState() == null || !row.getSessionState().isSendable())
                        return Mono.error(new IllegalStateException("Session " + sessionId + " is "
                                + row.getSessionState() + " and cannot send: " + row.getSessionReason()));

                    return this.bridgeClient.sendMedia(
                            instance.getBaseUrl(), sessionId, to, filePath, kind, mimeType, fileName, caption,
                            voiceNote, resourceType);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.sendMedia"));
    }

    public Mono<Map<String, Object>> sendText(MessageAccess access, String sessionId, String to, String text) {
        return this.withInstance(access, sessionId, (row, instance) -> {
                    if (row.getSessionState() == null || !row.getSessionState().isSendable())
                        return Mono.error(new IllegalStateException("Session " + sessionId + " is "
                                + row.getSessionState() + " and cannot send: " + row.getSessionReason()));

                    return this.bridgeClient.sendText(instance.getBaseUrl(), sessionId, to, text);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.sendText"));
    }

    /** Marks a thread read, called when a person opens it rather than when a message arrives. */
    public Mono<Void> markRead(
            MessageAccess access, String sessionId, String chat, String sender, List<String> messageIds) {

        return this.withInstance(access, sessionId, (row, instance) ->
                        this.bridgeClient.markRead(instance.getBaseUrl(), sessionId, chat, sender, messageIds))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.markRead"));
    }

    public Mono<Map<String, Object>> onWhatsApp(MessageAccess access, String sessionId, String number) {
        return this.withInstance(access, sessionId, (row, instance) ->
                        this.bridgeClient.onWhatsApp(instance.getBaseUrl(), sessionId, number))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeSessionService.onWhatsApp"));
    }

    /**
     * Resolves a session to its instance and runs an operation against it.
     *
     * <p>Tenant-checked, because the session id is the only thing the caller supplies and it is a
     * short code. Without the check, knowing a code would be enough to send from another tenant's
     * WhatsApp number, which is about as bad as it gets.
     */
    private <T> Mono<T> withInstance(
            MessageAccess access,
            String sessionId,
            java.util.function.BiFunction<WhatsappPhoneNumber, BridgeInstance, Mono<T>> operation) {

        return this.withRow(access, sessionId).flatMap(row -> {
            if (row.getBridgeInstanceId() == null)
                return Mono.error(new IllegalStateException(
                        "Session " + sessionId + " is not on any instance. It needs to be linked again."));

            return this.instanceDao
                    .findByInstanceId(row.getBridgeInstanceId())
                    .switchIfEmpty(Mono.error(new IllegalStateException("Session " + sessionId
                            + " is assigned to instance " + row.getBridgeInstanceId()
                            + ", which is not registered.")))
                    .flatMap(instance -> operation.apply(row, instance));
        });
    }

    /**
     * Loads a session row and checks it belongs to the caller.
     *
     * <p>Split out of {@link #withInstance} because unlinking needs the row and the tenancy check
     * without the requirement that some instance currently holds it.
     */
    private Mono<WhatsappPhoneNumber> withRow(MessageAccess access, String sessionId) {

        return this.sessionDao
                .getBySessionIdInternal(sessionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No WhatsApp session " + sessionId + ".")))
                .flatMap(row -> {
                    if (!row.getAppCode().equals(access.getAppCode())
                            || !row.getClientCode().equals(access.getClientCode())) {
                        // Logged, and answered as if it did not exist. Telling the caller it exists
                        // but belongs to somebody else confirms the code is real.
                        logger.error(
                                "App {} client {} tried to use session {}, which belongs to app {} client {}.",
                                access.getAppCode(),
                                access.getClientCode(),
                                sessionId,
                                row.getAppCode(),
                                row.getClientCode());
                        return Mono.error(new IllegalArgumentException("No WhatsApp session " + sessionId + "."));
                    }

                    return Mono.just(row);
                });
    }
}
