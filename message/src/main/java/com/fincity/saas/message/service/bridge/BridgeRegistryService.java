package com.fincity.saas.message.service.bridge;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.bridge.BridgeInstanceDAO;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.bridge.BridgeInstance;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.bridge.BridgeInstanceState;
import com.fincity.saas.message.model.request.bridge.BridgeHeartbeatRequest;
import com.fincity.saas.message.model.request.bridge.BridgeRegisterRequest;
import com.fincity.saas.message.model.request.bridge.BridgeRetiredSession;
import com.fincity.saas.message.model.request.bridge.BridgeSessionSnapshot;
import com.fincity.saas.message.model.response.bridge.BridgeControlResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The fleet registry: who is out there, what they hold, and what they should be running.
 *
 * <p>Eureka-shaped but purpose-built, because what is needed here is session ownership rather than
 * service discovery and Eureka has no concept of it. The instances are also cross-region and not
 * JVMs, so none of the existing mesh reaches them.
 */
@Service
public class BridgeRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(BridgeRegistryService.class);

    private final BridgeInstanceDAO instanceDao;
    private final WhatsappPhoneNumberDAO sessionDao;

    /** Heartbeats arrive every 15s; three misses is the DOWN threshold. */
    @Value("${message.bridge.heartbeat-timeout-seconds:45}")
    private long heartbeatTimeoutSeconds;

    public BridgeRegistryService(BridgeInstanceDAO instanceDao, WhatsappPhoneNumberDAO sessionDao) {
        this.instanceDao = instanceDao;
        this.sessionDao = sessionDao;
    }

    /**
     * Admits an instance to the fleet and reconciles what it says it holds.
     *
     * <p>Registration is repeated rather than one-shot: on startup, and again whenever heartbeats
     * have been failing long enough that the instance suspects this service restarted and forgot it.
     * An instance we do not know about receives no placements and no deployments while looking
     * perfectly healthy from the outside, so re-announcing must always be accepted.
     */
    public Mono<BridgeControlResponse> register(BridgeRegisterRequest request) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        BridgeInstance instance = new BridgeInstance()
                .setInstanceId(request.getInstanceId())
                .setBaseUrl(request.getBaseUrl())
                .setCountries(joinCountries(request.getCountries()))
                .setSessionCap(request.getSessionCap() == null ? 0 : request.getSessionCap())
                .setVersion(request.getVersion())
                .setActiveSessions(countActive(request.getHeldSessions()))
                .setHeldSessions(request.getHeldSessions() == null
                        ? 0
                        : request.getHeldSessions().size());

        return this.instanceDao
                .register(instance, now)
                .doOnNext(rows -> logger.info(
                        "Bridge {} registered: version {}, countries {}, cap {}, holding {} session(s).",
                        request.getInstanceId(),
                        request.getVersion(),
                        instance.getCountries(),
                        instance.getSessionCap(),
                        instance.getHeldSessions()))
                .then(this.applySnapshots(request.getInstanceId(), request.getHeldSessions()))
                .then(this.reconcile(request.getInstanceId(), request.getHeldSessions()))
                .then(this.respond(request.getInstanceId(), List.of()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeRegistryService.register"));
    }

    /**
     * Records a heartbeat, applies reported session state, and releases acknowledged retirements.
     *
     * <p>Distinct from a Prometheus scrape and not interchangeable with one. A scrape says the
     * process answers; this says it believes it is healthy and reports what it holds. A wedged bridge
     * still serves metrics, so a stale heartbeat with {@code up == 1} is its own alert.
     */
    public Mono<BridgeControlResponse> heartbeat(String instanceId, BridgeHeartbeatRequest request) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int active = request.getActiveSessions() == null ? 0 : request.getActiveSessions();
        int held = request.getHeldSessions() == null ? 0 : request.getHeldSessions();

        return this.instanceDao
                .findByInstanceId(instanceId)
                // A heartbeat from an instance we have never seen is not an error to swallow. It
                // means this service restarted and lost the fleet, or the instance was started
                // without registering. Either way the answer is the same: tell it, so it
                // re-registers with its held sessions and reconciliation runs.
                .switchIfEmpty(Mono.defer(() -> {
                    logger.warn(
                            "Heartbeat from unregistered bridge {}. Answering so it re-registers"
                                    + " and reconciliation can run.",
                            instanceId);
                    return Mono.empty();
                }))
                .flatMap(existing -> this.instanceDao
                        .recordHeartbeat(instanceId, active, held, existing.getVersion(), now)
                        .thenReturn(existing))
                .then(this.applySnapshots(instanceId, request.getSessions()))
                .then(this.releaseRetired(request.getRetired()))
                .flatMap(accepted -> this.respond(instanceId, accepted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeRegistryService.heartbeat"));
    }

    /**
     * Writes back the state the bridge reports for each session it holds.
     *
     * <p>The bridge is the authority here, not this service. It is the process actually holding the
     * socket, and a state this service inferred would be a guess that disagrees with reality at
     * exactly the moment somebody is looking at it during an incident.
     */
    private Mono<Void> applySnapshots(String instanceId, List<BridgeSessionSnapshot> snapshots) {

        if (snapshots == null || snapshots.isEmpty()) return Mono.empty();

        return Flux.fromIterable(snapshots)
                .filter(s -> s.getId() != null && s.getState() != null)
                .concatMap(s -> this.sessionDao
                        .applySessionState(
                                s.getId(),
                                s.getState(),
                                s.getReason(),
                                s.getCountry(),
                                toUtc(s.getLinkedAt()),
                                toUtc(s.getStateSince()))
                        .doOnNext(rows -> {
                            if (rows == 0)
                                logger.warn(
                                        "Bridge {} reported session {} which matches no row here."
                                                + " Reconciliation will flag it as a stray.",
                                        instanceId,
                                        s.getId());
                        })
                        // One bad row must not stop the rest of the fleet's state being recorded.
                        .onErrorResume(e -> {
                            logger.error("Could not apply state for session {}.", s.getId(), e);
                            return Mono.just(0);
                        }))
                .then();
    }

    /**
     * Diffs what an instance says it holds against what this service has assigned to it.
     *
     * <p>This is why registration carries a body at all, and it is the part worth building
     * carefully. Two directions, and they are not equally serious:
     *
     * <ul>
     *   <li><b>Orphan</b>: assigned here, not present. The session is gone; the customer will have
     *       to re-link. Bad, visible, recoverable.
     *   <li><b>Stray</b>: present here, assigned elsewhere. This is the dangerous one. It means two
     *       processes may be holding one device store, which corrupts the Signal ratchet in a way no
     *       amount of retrying fixes. It is a page, not a warning.
     * </ul>
     */
    private Mono<Void> reconcile(String instanceId, List<BridgeSessionSnapshot> held) {

        Set<String> reported = held == null
                ? Set.of()
                : held.stream()
                        .map(BridgeSessionSnapshot::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        return this.sessionDao.listByInstance(instanceId).flatMap(assigned -> {
            Set<String> assignedIds = assigned.stream()
                    .map(WhatsappPhoneNumber::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<String> orphans =
                    assignedIds.stream().filter(id -> !reported.contains(id)).toList();

            List<String> unaccounted =
                    reported.stream().filter(id -> !assignedIds.contains(id)).toList();

            if (!orphans.isEmpty())
                logger.error(
                        "Bridge {} is assigned {} session(s) it does not hold: {}."
                                + " Those numbers are offline and need re-linking.",
                        instanceId,
                        orphans.size(),
                        orphans);

            if (unaccounted.isEmpty()) return Mono.empty();

            // Resolved one by one, because "assigned to another instance" and "unknown here" are
            // different problems: the first may be two live processes on one device store, the
            // second is a session this service has no record of at all.
            return Flux.fromIterable(unaccounted)
                    .concatMap(id -> this.sessionDao
                            .getBySessionIdInternal(id)
                            .doOnNext(row -> logger.error(
                                    "STRAY: bridge {} holds session {} but it is assigned to {}."
                                        + " Two processes may be on one device store, which corrupts the Signal"
                                        + " ratchet. Stop one of them before anything else.",
                                    instanceId,
                                    id,
                                    row.getBridgeInstanceId() == null ? "no instance" : row.getBridgeInstanceId()))
                            .switchIfEmpty(Mono.fromRunnable(() -> logger.error(
                                    "Bridge {} holds session {}, which this service has no record of."
                                            + " Its messages will be dropped at ingest until it is reconciled.",
                                    instanceId,
                                    id))))
                    .then();
        });
    }

    /**
     * Releases the assignments for sessions the bridge has retired.
     *
     * <p>Returns only the ids actually released, because the bridge repeats anything unacknowledged.
     * Echoing an id we failed to release would leave the assignment pointing at a session nobody
     * holds, and the customer permanently unable to link that number again: the unique key on the
     * linked number only ignores rows whose instance is null.
     */
    private Mono<List<String>> releaseRetired(List<BridgeRetiredSession> retired) {

        if (retired == null || retired.isEmpty()) return Mono.just(List.of());

        List<String> accepted = new ArrayList<>();

        return Flux.fromIterable(retired)
                .filter(r -> r.getSessionId() != null && r.getState() != null)
                .concatMap(r -> this.sessionDao
                        .releaseAssignment(
                                r.getSessionId(),
                                r.getState(),
                                r.getReason(),
                                toUtcOrNow(r.getAt()))
                        .doOnNext(rows -> {
                            accepted.add(r.getSessionId());
                            logger.info(
                                    "Released session {} ({}): {}. It can now be placed fresh on"
                                            + " whichever instance has room.",
                                    r.getSessionId(),
                                    r.getState(),
                                    r.getReason());
                        })
                        // Not acknowledged, so the bridge sends it again on the next heartbeat. A
                        // repeat costs nothing; a lost release strands the number.
                        .onErrorResume(e -> {
                            logger.error(
                                    "Could not release retired session {}; it will be reported again.",
                                    r.getSessionId(),
                                    e);
                            return Mono.just(0);
                        }))
                .then(Mono.fromSupplier(() -> List.copyOf(accepted)));
    }

    private Mono<BridgeControlResponse> respond(String instanceId, List<String> acceptedRetirements) {
        return this.instanceDao
                .findByInstanceId(instanceId)
                .map(instance -> new BridgeControlResponse()
                        .setDesiredImage(instance.getDesiredImage())
                        .setDraining(instance.getState() == BridgeInstanceState.DRAINING)
                        .setAcceptedRetirements(acceptedRetirements))
                .defaultIfEmpty(new BridgeControlResponse().setAcceptedRetirements(acceptedRetirements));
    }

    /**
     * Marks instances that have stopped heartbeating as DOWN.
     *
     * <p>Their sessions stay assigned. Not failing them over is the deliberate choice: a session has
     * exactly one home, and giving it a second is how two processes end up on one device store.
     */
    public Mono<Integer> sweepStaleInstances() {
        LocalDateTime staleBefore =
                LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofSeconds(this.heartbeatTimeoutSeconds));

        return this.instanceDao
                .markStaleDown(staleBefore)
                .doOnNext(marked -> {
                    if (marked > 0)
                        logger.error(
                                "{} bridge instance(s) missed heartbeats and are now DOWN."
                                        + " Every session on them is offline.",
                                marked);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeRegistryService.sweepStaleInstances"));
    }

    public Mono<List<BridgeInstance>> listFleet() {
        return this.instanceDao.listAll();
    }

    /**
     * Declares the image an instance should be running.
     *
     * <p>This is the whole of the deployment mechanism on this side. CI declares; the host agent
     * reads the declaration off its own heartbeat and rolls itself. Nothing here reaches into the
     * bridge host, which is what keeps Mumbai free of inbound SSH and of a key to distribute.
     */
    public Mono<Integer> release(String country, String instanceId, String image) {

        if (instanceId != null && !instanceId.isBlank())
            return this.instanceDao
                    .setDesiredImage(instanceId, image)
                    .doOnNext(rows -> logger.info("Released {} to instance {}.", image, instanceId));

        return this.instanceDao.listAll().flatMap(all -> {
            List<BridgeInstance> targets =
                    all.stream().filter(i -> i.serves(country)).toList();

            if (targets.isEmpty()) {
                logger.error("Release of {} for country {} matched no instances.", image, country);
                return Mono.just(0);
            }

            return Flux.fromIterable(targets)
                    .concatMap(i -> this.instanceDao.setDesiredImage(i.getInstanceId(), image))
                    .reduce(0, Integer::sum)
                    .doOnNext(rows -> logger.info(
                            "Released {} to {} instance(s) serving {}. The roll is serial and each"
                                    + " instance is gated on re-registration, so a bad image stops at the first.",
                            image,
                            rows,
                            country));
        });
    }

    public Mono<Integer> setState(String instanceId, BridgeInstanceState state) {
        return this.instanceDao
                .setState(instanceId, state)
                .doOnNext(rows -> logger.info("Bridge {} set to {}.", instanceId, state));
    }

    private static int countActive(List<BridgeSessionSnapshot> sessions) {
        if (sessions == null) return 0;
        return (int) sessions.stream()
                .filter(s -> s.getState() != null && !s.getState().isTerminal())
                .count();
    }

    private static String joinCountries(List<String> countries) {
        if (countries == null || countries.isEmpty()) return "";
        return countries.stream()
                .filter(Objects::nonNull)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * The servers run UTC, so this is a representation change rather than a timezone conversion. Kept
     * explicit because doing it with the default zone would silently shift every timestamp on a
     * developer machine running IST.
     */
    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static LocalDateTime toUtcOrNow(Instant instant) {
        return instant == null ? LocalDateTime.now(ZoneOffset.UTC) : toUtc(instant);
    }
}
