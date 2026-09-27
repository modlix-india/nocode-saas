package com.fincity.saas.entity.processor.analytics.dao.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseAnalyticsDAO<R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>>
        extends AbstractDAO<R, ULong, D> {

    protected BaseAnalyticsDAO(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
        super(pojoClass, table, idField);
    }

    protected abstract Map<String, String> getBucketFilterFieldMappings();

    public <T extends BaseFilter<T>> Mono<AbstractCondition> createBucketConditions(
            ProcessorAccess access, T ticketBucketFilter) {
        return this.addBucketConditions(null, access, ticketBucketFilter);
    }

    public <T extends BaseFilter<T>> Mono<AbstractCondition> createBucketConditions(
            AbstractCondition condition, ProcessorAccess access, T ticketBucketFilter) {
        return this.addBucketConditions(condition, access, ticketBucketFilter);
    }

    public <T extends BaseFilter<T>> Mono<AbstractCondition> createBucketConditionsWithoutDate(
            ProcessorAccess access, T filter) {
        return this.addBucketConditionsWithoutDate(null, access, filter);
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> addBucketConditionsWithoutDate(
            AbstractCondition baseCondition, ProcessorAccess access, T filter) {

        Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();

        return Mono.zip(
                        this.getBaseAccessConditions(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getFilterAccessConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(condTuple -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    condTuple.getT1().ifPresent(conditions::add);
                    condTuple.getT2().ifPresent(conditions::add);

                    if (baseCondition != null && !baseCondition.isEmpty()) conditions.add(baseCondition);

                    return ComplexCondition.and(conditions.stream()
                            .filter(AbstractCondition::isNonEmpty)
                            .toList());
                });
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> addBucketConditions(
            AbstractCondition baseCondition, ProcessorAccess access, T filter) {

        Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();

        return Mono.zip(
                        this.getBaseAccessConditions(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getDateConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getFilterAccessConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(condTuple -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    condTuple.getT1().ifPresent(conditions::add);
                    condTuple.getT2().ifPresent(conditions::add);
                    condTuple.getT3().ifPresent(conditions::add);

                    if (baseCondition != null && !baseCondition.isEmpty()) conditions.add(baseCondition);

                    return ComplexCondition.and(conditions.stream()
                            .filter(AbstractCondition::isNonEmpty)
                            .toList());
                });
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> getFilterAccessConditions(
            T filter, Map<String, String> fieldMappings) {

        if (filter == null
                || filter.getAssignedUserIds() == null
                        && filter.getClientIds() == null
                        && filter.getCreatedByIds() == null) return Mono.empty();

        return Mono.zip(
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.assignedUserIds), filter.getAssignedUserIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.clientIds), filter.getClientIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.createdByIds), filter.getCreatedByIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(tuple -> {
                    List<AbstractCondition> conds = new ArrayList<>();
                    tuple.getT1().ifPresent(conds::add);
                    tuple.getT2().ifPresent(conds::add);
                    tuple.getT3().ifPresent(conds::add);
                    return ComplexCondition.and(conds);
                });
    }

    protected Mono<Optional<AbstractCondition>> getAdditionalAccessConditions(ProcessorAccess access) {
        return Mono.just(Optional.empty());
    }

    private Mono<AbstractCondition> getBaseAccessConditions(ProcessorAccess access, Map<String, String> fieldMappings) {

        return Mono.zip(
                        this.getAppCodeCondition(access).map(Optional::of).defaultIfEmpty(Optional.empty()),
                        this.getClientCodeCondition(access).map(Optional::of).defaultIfEmpty(Optional.empty()),
                        this.getUserConditions(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getClientIdCondition(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getAdditionalAccessConditions(access))
                .flatMap(condTuple -> {
                    List<AbstractCondition> appClientConditions = new ArrayList<>();
                    condTuple.getT1().ifPresent(appClientConditions::add);
                    condTuple.getT2().ifPresent(appClientConditions::add);

                    AbstractCondition appClientCondition = ComplexCondition.and(appClientConditions);

                    List<AbstractCondition> userClientConditions = new ArrayList<>();

                    condTuple.getT3().ifPresent(userClientConditions::add);
                    condTuple.getT4().ifPresent(userClientConditions::add);

                    Mono<AbstractCondition> userClientCondition = access.isOutsideUser()
                            ? Mono.just(ComplexCondition.and(userClientConditions))
                            : this.visibilityCondition(access, fieldMappings, userClientConditions);

                    return userClientCondition.map(resolved -> {
                        AbstractCondition finalAccessCondition = condTuple.getT5()
                                .filter(AbstractCondition::isNonEmpty)
                                .<AbstractCondition>map(additional -> ComplexCondition.or(resolved, additional))
                                .orElse(resolved);

                        return ComplexCondition.and(appClientCondition, finalAccessCondition);
                    });
                });
    }

    /**
     * Above this many visible rows the plain OR is left alone.
     *
     * Measured on production against leadzump/FIN, 160,563 tickets. The OR costs a flat ~220ms
     * whatever the scope, because it always scans every row of the tenant. This path costs two
     * things that both grow with the number of visible rows: the union that resolves the ids,
     * and the {@code ID IN (...)} that follows it.
     *
     * <pre>
     *   visible ids     union     IN (...)     total      the OR it replaces
     *        ~1,000     ~4ms          5ms      ~9ms                    220ms
     *         5,294     4.3ms        27ms      ~31ms                   220ms
     *        16,760    23.3ms        29ms      ~52ms                   221ms
     *        60,178     278ms        30ms     ~308ms                   236ms
     * </pre>
     *
     * The union itself only turns against us around 60,000 -- roughly a third of the tenant --
     * where the assignee branch stops being selective and MySQL falls back to the same
     * two-column index for it anyway, then pays for materialisation and de-duplication on top.
     * The cap is set well below that crossover on purpose: the second half of the round trip
     * sends every id back as a bind parameter, and 20,000 of them is a 109KB statement on every
     * analytics request. The win is concentrated at the small end (25x at a thousand ids, 7x at
     * five thousand, 4x at seventeen), so the cap buys nearly all of it at a fraction of the
     * statement size. Raising it trades wire and parse cost for a shrinking return.
     */
    private static final int VISIBLE_ID_CAP = 10000;

    /**
     * The access scope is "assigned to someone in my sub-org OR belonging to a client I manage".
     * Both columns are indexed -- IDX1_TICKETS_AC_CC_ASSIGNED_USER and
     * IDX2_TICKETS_AC_CC_CLIENT_ID -- but MySQL cannot serve an OR across two different columns
     * from one composite index, so it falls back to the (APP_CODE, CLIENT_CODE) prefix and
     * filters by hand: 77,442 rows examined for leadzump/FIN, on every analytics query, for
     * every endpoint that inherits this class. Distributing the AND over the OR does not help,
     * and an INDEX_MERGE hint is ignored, because both candidate indexes share that same leading
     * prefix so a single ref access always looks cheapest.
     * <p>
     * A UNION of the two branches does work -- each side becomes a covering range scan -- but a
     * UNION cannot be expressed in the AbstractCondition model the callers build their queries
     * from. So the union is run here, on its own, and its result replaces the OR with an
     * equality on the primary key.
     * <p>
     * That substitution is exact rather than approximate: the id set is by definition the set of
     * rows the original OR matched, so {@code ID IN (ids)} selects the same rows. It stays exact
     * whatever the caller ANDs or ORs around it, which is what makes this safe to do inside
     * access scoping.
     * <p>
     * Every path that cannot be resolved -- one side of the OR absent, no ids to match on, a
     * field the mapping does not cover, a set past the cap, or the query failing -- returns the
     * original OR. This can lose performance. It cannot widen what a user is allowed to see.
     */
    private Mono<AbstractCondition> visibilityCondition(
            ProcessorAccess access, Map<String, String> fieldMappings, List<AbstractCondition> userClientConditions) {

        AbstractCondition fallback = ComplexCondition.or(userClientConditions);

        // One branch only -- the common case for a user without BP access, where
        // getClientIdCondition returns empty. The OR collapses to a single indexed condition and
        // there is nothing to fix.
        if (userClientConditions.size() < 2) return Mono.just(fallback);

        List<ULong> users = access.getUserInherit() == null
                ? null
                : access.getUserInherit().getSubOrg();
        List<ULong> clients = access.getUserInherit() == null
                ? null
                : access.getUserInherit().getManagingClientIds();

        if (users == null || users.isEmpty() || clients == null || clients.isEmpty())
            return Mono.just(fallback);

        Field assignedField = this.getField(fieldMappings.get(BaseFilter.Fields.assignedUserIds));
        Field clientIdField = this.getField(fieldMappings.get(BaseFilter.Fields.clientIds));
        Field appCodeField = this.getField(AbstractFlowUpdatableDTO.Fields.appCode);
        Field clientCodeField = this.getField(AbstractFlowUpdatableDTO.Fields.clientCode);

        if (assignedField == null || clientIdField == null || appCodeField == null || clientCodeField == null)
            return Mono.just(fallback);

        // Mono.defer so that a failure while BUILDING the query is an error signal like any
        // other and reaches onErrorReturn below. Thrown during assembly instead, it would escape
        // the operator chain entirely and fail the request -- turning a performance optimisation
        // into an outage.
        return Mono.defer(() -> {
                    // The same tenant scoping the outer query applies. Without it each branch
                    // would scan every tenant's rows and the index would be no use here either.
                    Condition scope = appCodeField
                            .eq(access.getAppCode())
                            .and(clientCodeField.eq(access.getEffectiveClientCode()));

                    Select<? extends Record1<ULong>> visibleIds = this.dslContext
                            .select(this.idField)
                            .from(this.table)
                            .where(scope.and(assignedField.in(users)))
                            .union(this.dslContext
                                    .select(this.idField)
                                    .from(this.table)
                                    .where(scope.and(clientIdField.in(clients))));

                    return Flux.from(visibleIds)
                            .map(Record1::value1)
                            // One past the cap is enough to know the cap was passed, without
                            // dragging the whole set across the wire to find out.
                            .take(VISIBLE_ID_CAP + 1L)
                            .collectList();
                })
                .map(ids -> {
                    // Empty means this user can see nothing, which is NOT the same as no filter.
                    // An IN over an empty list is dropped by the condition model, so the original
                    // OR -- which correctly matches nothing -- has to stand.
                    if (ids.isEmpty() || ids.size() > VISIBLE_ID_CAP) return fallback;

                    return (AbstractCondition) new FilterCondition()
                            .setField(AbstractDTO.Fields.id)
                            .setOperator(FilterConditionOperator.IN)
                            .setMultiValue(ids);
                })
                .onErrorReturn(fallback);
    }

    private Mono<AbstractCondition> getAppCodeCondition(ProcessorAccess access) {
        return Mono.just(FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, access.getAppCode()));
    }

    private Mono<AbstractCondition> getClientCodeCondition(ProcessorAccess access) {
        return Mono.just(
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, access.getEffectiveClientCode()));
    }

    private Mono<AbstractCondition> getUserConditions(ProcessorAccess access, Map<String, String> fieldMappings) {

        if (access.isOutsideUser())
            return this.makeIn(
                    fieldMappings.get(BaseFilter.Fields.createdByIds),
                    access.getUserInherit().getSubOrg());

        return this.makeIn(
                fieldMappings.get(BaseFilter.Fields.assignedUserIds),
                access.getUserInherit().getSubOrg());
    }

    private Mono<AbstractCondition> getClientIdCondition(ProcessorAccess access, Map<String, String> fieldMappings) {

        if (access.isOutsideUser())
            return Mono.just(FilterCondition.make(
                    fieldMappings.get(BaseFilter.Fields.clientIds),
                    access.getUser().getClientId()));

        if (!access.isHasBpAccess()) return Mono.empty();

        return this.makeIn(
                fieldMappings.get(BaseFilter.Fields.clientIds),
                access.getUserInherit().getManagingClientIds());
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> getDateConditions(
            T filter, Map<String, String> fieldMappings) {

        LocalDateTime startDate = filter.getStartDate();
        LocalDateTime endDate = filter.getEndDate();

        if (startDate == null && endDate == null) return Mono.empty();

        if (startDate != null && endDate != null)
            return Mono.just(new FilterCondition()
                    .setField(fieldMappings.get(BaseFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.BETWEEN)
                    .setValue(startDate)
                    .setToValue(endDate));

        if (startDate != null)
            return Mono.just(new FilterCondition()
                    .setField(fieldMappings.get(BaseFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.GREATER_THAN_EQUAL)
                    .setValue(startDate));

        return Mono.just(new FilterCondition()
                .setField(fieldMappings.get(BaseFilter.Fields.endDate))
                .setOperator(FilterConditionOperator.LESS_THAN_EQUAL)
                .setValue(endDate));
    }

    protected <T> Mono<AbstractCondition> makeIn(String mappedField, List<T> values) {

        if (mappedField == null) return Mono.empty();

        if (values == null || values.isEmpty()) return Mono.empty();

        return Mono.just(new FilterCondition()
                .setField(mappedField)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(values));
    }
}
