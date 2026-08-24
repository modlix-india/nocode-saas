package com.fincity.saas.message.dao.bridge;

import static com.fincity.saas.message.jooq.tables.MessageBridgeInstances.MESSAGE_BRIDGE_INSTANCES;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.message.dto.bridge.BridgeInstance;
import com.fincity.saas.message.enums.bridge.BridgeInstanceState;
import com.fincity.saas.message.jooq.tables.records.MessageBridgeInstancesRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The fleet registry.
 *
 * <p>Extends {@link AbstractDAO} rather than this service's tenant-scoped bases, because an instance
 * is infrastructure rather than tenant data and every access-scoped helper on those bases would be
 * filtering on columns this table deliberately does not have.
 */
@Component
public class BridgeInstanceDAO extends AbstractDAO<MessageBridgeInstancesRecord, ULong, BridgeInstance> {

    protected BridgeInstanceDAO() {
        super(BridgeInstance.class, MESSAGE_BRIDGE_INSTANCES, MESSAGE_BRIDGE_INSTANCES.ID);
    }

    /**
     * Records a registration, creating the instance or refreshing what it declares.
     *
     * <p>An upsert rather than a create, because registration is repeated: on startup, and again
     * whenever heartbeats have been failing long enough to suspect this service restarted and forgot
     * the fleet. Making it fail on a second call would leave a live instance unable to re-announce
     * itself, which is the one moment it most needs to.
     *
     * <p>Two things are deliberately not overwritten:
     *
     * <ul>
     *   <li>{@code DESIRED_IMAGE}, which is set by a release and would otherwise be erased by the
     *       very restart the release triggered, leaving the instance running the old image and
     *       nothing left to tell it otherwise.
     *   <li>{@code DRAINING}, which somebody set on purpose. An instance being decommissioned must
     *       not put itself back into placement by restarting.
     * </ul>
     */
    public Mono<Integer> register(BridgeInstance instance, LocalDateTime now) {

        var stateOnRegister = DSL.when(
                        MESSAGE_BRIDGE_INSTANCES.STATE.eq(BridgeInstanceState.DRAINING),
                        DSL.val(BridgeInstanceState.DRAINING))
                .otherwise(DSL.val(BridgeInstanceState.UP));

        return Mono.from(this.dslContext
                .insertInto(MESSAGE_BRIDGE_INSTANCES)
                .set(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID, instance.getInstanceId())
                .set(MESSAGE_BRIDGE_INSTANCES.BASE_URL, instance.getBaseUrl())
                .set(MESSAGE_BRIDGE_INSTANCES.COUNTRIES, instance.getCountries())
                .set(MESSAGE_BRIDGE_INSTANCES.SESSION_CAP, UInteger.valueOf(instance.getSessionCap()))
                .set(MESSAGE_BRIDGE_INSTANCES.VERSION, instance.getVersion())
                .set(MESSAGE_BRIDGE_INSTANCES.STATE, BridgeInstanceState.UP)
                .set(MESSAGE_BRIDGE_INSTANCES.ACTIVE_SESSIONS, UInteger.valueOf(instance.getActiveSessions()))
                .set(MESSAGE_BRIDGE_INSTANCES.HELD_SESSIONS, UInteger.valueOf(instance.getHeldSessions()))
                .set(MESSAGE_BRIDGE_INSTANCES.LAST_HEARTBEAT_AT, now)
                .set(MESSAGE_BRIDGE_INSTANCES.MISSED_HEARTBEATS, UInteger.valueOf(0))
                .set(MESSAGE_BRIDGE_INSTANCES.IS_ACTIVE, Boolean.TRUE)
                .onDuplicateKeyUpdate()
                .set(MESSAGE_BRIDGE_INSTANCES.BASE_URL, instance.getBaseUrl())
                .set(MESSAGE_BRIDGE_INSTANCES.COUNTRIES, instance.getCountries())
                .set(MESSAGE_BRIDGE_INSTANCES.SESSION_CAP, UInteger.valueOf(instance.getSessionCap()))
                .set(MESSAGE_BRIDGE_INSTANCES.VERSION, instance.getVersion())
                .set(MESSAGE_BRIDGE_INSTANCES.STATE, stateOnRegister)
                .set(MESSAGE_BRIDGE_INSTANCES.ACTIVE_SESSIONS, UInteger.valueOf(instance.getActiveSessions()))
                .set(MESSAGE_BRIDGE_INSTANCES.HELD_SESSIONS, UInteger.valueOf(instance.getHeldSessions()))
                .set(MESSAGE_BRIDGE_INSTANCES.LAST_HEARTBEAT_AT, now)
                .set(MESSAGE_BRIDGE_INSTANCES.MISSED_HEARTBEATS, UInteger.valueOf(0))
                .set(MESSAGE_BRIDGE_INSTANCES.IS_ACTIVE, Boolean.TRUE));
    }

    public Mono<BridgeInstance> findByInstanceId(String instanceId) {
        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_BRIDGE_INSTANCES)
                        .where(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.eq(instanceId)))
                .map(rec -> rec.into(BridgeInstance.class));
    }

    /**
     * Records a heartbeat and clears the miss counter.
     *
     * <p>Also lifts the instance out of {@code DOWN}: a heartbeat is proof it is back, and leaving it
     * marked down would keep it out of placement until somebody noticed by hand. {@code DRAINING} is
     * again preserved, for the same reason as on registration.
     */
    public Mono<Integer> recordHeartbeat(String instanceId, int active, int held, String version, LocalDateTime now) {

        var revived = DSL.when(
                        MESSAGE_BRIDGE_INSTANCES.STATE.eq(BridgeInstanceState.DRAINING),
                        DSL.val(BridgeInstanceState.DRAINING))
                .otherwise(DSL.val(BridgeInstanceState.UP));

        return Mono.from(this.dslContext
                .update(MESSAGE_BRIDGE_INSTANCES)
                .set(MESSAGE_BRIDGE_INSTANCES.ACTIVE_SESSIONS, UInteger.valueOf(Math.max(active, 0)))
                .set(MESSAGE_BRIDGE_INSTANCES.HELD_SESSIONS, UInteger.valueOf(Math.max(held, 0)))
                .set(MESSAGE_BRIDGE_INSTANCES.LAST_HEARTBEAT_AT, now)
                .set(MESSAGE_BRIDGE_INSTANCES.MISSED_HEARTBEATS, UInteger.valueOf(0))
                .set(MESSAGE_BRIDGE_INSTANCES.VERSION, version)
                .set(MESSAGE_BRIDGE_INSTANCES.STATE, revived)
                .where(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.eq(instanceId)));
    }

    /**
     * Marks instances that have stopped heartbeating as {@code DOWN}.
     *
     * <p>Their sessions stay assigned to them and are deliberately not failed over. A session has
     * exactly one home; a second home means two processes on one device store, which is the
     * unrecoverable failure this whole design is arranged to prevent. Down is down, and it is a page,
     * not a reroute.
     */
    public Mono<Integer> markStaleDown(LocalDateTime staleBefore) {
        return Mono.from(this.dslContext
                .update(MESSAGE_BRIDGE_INSTANCES)
                .set(MESSAGE_BRIDGE_INSTANCES.STATE, BridgeInstanceState.DOWN)
                .where(MESSAGE_BRIDGE_INSTANCES.IS_ACTIVE.eq(Boolean.TRUE))
                .and(MESSAGE_BRIDGE_INSTANCES.STATE.ne(BridgeInstanceState.DOWN))
                .and(MESSAGE_BRIDGE_INSTANCES.LAST_HEARTBEAT_AT.isNull()
                        .or(MESSAGE_BRIDGE_INSTANCES.LAST_HEARTBEAT_AT.lt(staleBefore))));
    }

    /** Every registered instance, for the fleet view and for reconciliation. */
    public Mono<List<BridgeInstance>> listAll() {
        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_BRIDGE_INSTANCES)
                        .orderBy(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.asc()))
                .map(rec -> rec.into(BridgeInstance.class))
                .collectList();
    }

    /**
     * Candidates for placement, before the country and cap filters are applied in memory.
     *
     * <p>Read as a small list and filtered in Java rather than in SQL, because {@code COUNTRIES} is a
     * comma-separated column and a {@code LIKE '%IN%'} against it would match {@code SG,IN} and
     * {@code IND} alike. The fleet is dozens of rows, so there is nothing to gain by being clever
     * here and a real correctness trap in trying.
     */
    public Mono<List<BridgeInstance>> listPlaceable() {
        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_BRIDGE_INSTANCES)
                        .where(MESSAGE_BRIDGE_INSTANCES.IS_ACTIVE.eq(Boolean.TRUE))
                        .and(MESSAGE_BRIDGE_INSTANCES.STATE.eq(BridgeInstanceState.UP))
                        .orderBy(MESSAGE_BRIDGE_INSTANCES.ACTIVE_SESSIONS.asc()))
                .map(rec -> rec.into(BridgeInstance.class))
                .collectList();
    }

    /** Sets the image a release wants running, for one instance or for a whole country. */
    public Mono<Integer> setDesiredImage(String instanceId, String image) {
        return Mono.from(this.dslContext
                .update(MESSAGE_BRIDGE_INSTANCES)
                .set(MESSAGE_BRIDGE_INSTANCES.DESIRED_IMAGE, image)
                .where(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.eq(instanceId)));
    }

    public Mono<Integer> setState(String instanceId, BridgeInstanceState state) {
        return Mono.from(this.dslContext
                .update(MESSAGE_BRIDGE_INSTANCES)
                .set(MESSAGE_BRIDGE_INSTANCES.STATE, state)
                .where(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.eq(instanceId)));
    }

    public Mono<Integer> recordError(String instanceId, String error) {
        return Mono.from(this.dslContext
                .update(MESSAGE_BRIDGE_INSTANCES)
                .set(
                        MESSAGE_BRIDGE_INSTANCES.LAST_ERROR,
                        error == null ? null : error.substring(0, Math.min(error.length(), 2000)))
                .where(MESSAGE_BRIDGE_INSTANCES.INSTANCE_ID.eq(instanceId)));
    }
}
