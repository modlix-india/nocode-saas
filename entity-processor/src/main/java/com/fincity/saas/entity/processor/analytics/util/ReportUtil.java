package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.DateCount;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.EntityCount;
import com.fincity.saas.entity.processor.analytics.model.EntityDateCount;
import com.fincity.saas.entity.processor.analytics.model.EntityEntityCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.StatusNameCount;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseStatusCount;
import com.fincity.saas.entity.processor.analytics.model.base.PerCount;
import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.common.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.common.PerValueCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.util.DatePair;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@UtilityClass
public class ReportUtil {

    private static final String TOTAL = "Total";
    private static final ULong TOTAL_ID = ULong.MIN;
    private static final int PARALLEL_THRESHOLD = Math.max(100, ForkJoinPool.getCommonPoolParallelism() * 4);

    private static final Comparator<StatusNameCount> statusNameComparator =
            Comparator.comparing(StatusNameCount::getName);

    private static final Comparator<StatusNameCount> statusNameTotalComparator = Comparator.comparing(
                    (StatusNameCount status) -> TOTAL.equalsIgnoreCase(status.getName()) ? 0 : 1)
            .thenComparing(statusNameComparator);

    /* -----------------------------------------------------------------------
     * Date based reports
     * ----------------------------------------------------------------------- */

    public static Flux<DateStatusCount> toDateStatusCounts( // NOSONAR
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            BaseFilter.ReportOptions options) {

        if (perDateCountList.isEmpty() && !options.includeZero()) return Flux.empty();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(options.totalDatePair(), options.timePeriod(), perDateCountList);

        requiredValueList = resolveRequiredValuesIfMissing(requiredValueList, perDateCountList, false);

        Set<String> groupedValues = options.includeZero()
                ? perDateCountList.stream().map(PerDateCount::getGroupedValue).collect(Collectors.toSet())
                : Set.of();

        List<IdAndValue<ULong, String>> finalRequired = requiredValueList;

        boolean shouldParallelize = datePairMap.size() > PARALLEL_THRESHOLD;

        Flux<Map.Entry<DatePair, List<PerDateCount>>> baseFlux = Flux.fromIterable(datePairMap.entrySet())
                .filter(entry -> options.includeZero() || !entry.getValue().isEmpty());

        if (shouldParallelize) {
            return baseFlux.parallel()
                    .runOn(Schedulers.parallel())
                    .flatMap(entry -> processDateEntry(entry, finalRequired, groupedValues, options))
                    .sequential()
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCounts"));
        }

        return baseFlux.flatMap(entry -> processDateEntry(entry, finalRequired, groupedValues, options))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCounts"));
    }

    private static Mono<DateStatusCount> processDateEntry(
            Map.Entry<DatePair, List<PerDateCount>> entry,
            List<IdAndValue<ULong, String>> requiredValueList,
            Set<String> groupedValues,
            BaseFilter.ReportOptions options) {

        String mapValue = requiredValueList.getFirst().getValue();

        List<PerDateCount> dateGroupedListWithZeros =
                options.includeZero() ? buildListWithZeros(entry, groupedValues, mapValue) : entry.getValue();

        return toStatusCountsGroupedValue(
                        dateGroupedListWithZeros,
                        requiredValueList,
                        options.includeZero(),
                        options.includePercentage(),
                        options.includeTotal(),
                        options.includeNone() != null && options.includeNone())
                .collectList()
                .map(statusCounts -> {
                    List<StatusNameCount> processed = options.includePercentage()
                            ? addPercentage(statusCounts, options.includeTotal())
                            : statusCounts;
                    processed.sort(options.includeTotal() ? statusNameTotalComparator : statusNameComparator);
                    return new DateStatusCount().setDatePair(entry.getKey()).setStatusCount(processed);
                });
    }

    private static List<PerDateCount> buildListWithZeros(
            Map.Entry<DatePair, List<PerDateCount>> entry, Set<String> groupedValues, String mapValue) {

        List<PerDateCount> result = new ArrayList<>(entry.getValue().size() + groupedValues.size());
        result.addAll(entry.getValue());

        Set<String> existingValues =
                entry.getValue().stream().map(PerDateCount::getGroupedValue).collect(Collectors.toSet());

        groupedValues.stream()
                .filter(value -> !existingValues.contains(value))
                .forEach(value -> result.add(new PerDateCount()
                        .setCount(0L)
                        .setDate(entry.getKey().getFirst())
                        .setGroupedValue(value)
                        .setMapValue(mapValue)));

        return result;
    }

    public static Flux<DateStatusCount> toDateStatusCountsAggregatedTotal(
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            BaseFilter.ReportOptions options) {

        if (perDateCountList.isEmpty() && !options.includeZero()) return Flux.empty();

        Map<Boolean, List<PerDateCount>> partitioned =
                perDateCountList.stream().collect(Collectors.partitioningBy(pdc -> pdc.getDate() == null));

        List<PerDateCount> totalEntries = options.includeTotal() ? partitioned.get(true) : List.of();
        List<PerDateCount> regularDateCountList = partitioned.get(false);

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(options.totalDatePair(), options.timePeriod(), regularDateCountList);

        if (options.includeTotal() && !totalEntries.isEmpty())
            datePairMap.values().forEach(list -> list.addAll(totalEntries));

        requiredValueList = resolveRequiredValuesIfMissing(requiredValueList, perDateCountList, true);

        List<IdAndValue<String, CountPercentage>> initialValues = buildInitialValues(
                perDateCountList, requiredValueList, options.includePercentage(), options.includeTotal());

        List<IdAndValue<String, CountPercentage>> uniqueInitialValues =
                buildUniqueInitialValues(initialValues, options.includePercentage());

        boolean shouldParallelize = datePairMap.size() > PARALLEL_THRESHOLD;
        Flux<Map.Entry<DatePair, List<PerDateCount>>> baseFlux = Flux.fromIterable(datePairMap.entrySet())
                .filter(entry -> options.includeZero() || !entry.getValue().isEmpty());

        if (shouldParallelize) {
            return baseFlux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> buildAggregatedTotalDateStatusCount(
                            entry,
                            initialValues,
                            uniqueInitialValues,
                            options.includePercentage(),
                            options.includeTotal()))
                    .sequential()
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCountsAggregatedTotal"));
        }

        return baseFlux.map(entry -> buildAggregatedTotalDateStatusCount(
                        entry, initialValues, uniqueInitialValues, options.includePercentage(), options.includeTotal()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCountsAggregatedTotal"));
    }

    // --- Date report helpers ---

    private static NavigableMap<DatePair, List<PerDateCount>> buildDatePairMap(
            DatePair totalDatePair, TimePeriod timePeriod, List<PerDateCount> perDateCountList) {

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                totalDatePair.toTimePeriodMap(timePeriod, LinkedList::new);

        int estimatedSize = datePairMap.isEmpty() ? 10 : Math.max(1, perDateCountList.size() / datePairMap.size());
        NavigableMap<DatePair, List<PerDateCount>> optimizedMap =
                totalDatePair.toTimePeriodMap(timePeriod, () -> new ArrayList<>(estimatedSize));

        for (PerDateCount pdc : perDateCountList) {
            DatePair datePair = DatePair.findContainingDate(pdc.getDate(), optimizedMap);
            if (datePair != null) optimizedMap.get(datePair).add(pdc);
        }

        return optimizedMap;
    }

    private static List<IdAndValue<ULong, String>> resolveRequiredValuesIfMissing(
            List<IdAndValue<ULong, String>> requiredValueList,
            List<PerDateCount> perDateCountList,
            boolean excludeHashPrefixedValues) {

        if (requiredValueList != null) return requiredValueList;

        Stream<IdAndValue<ULong, String>> stream =
                perDateCountList.stream().map(perDateCount -> IdAndValue.of(ULong.MIN, perDateCount.getMapValue()));

        if (excludeHashPrefixedValues)
            stream = stream.filter(
                    idValue -> idValue.getValue() == null || !idValue.getValue().startsWith("#"));

        return stream.distinct().toList();
    }

    private static List<IdAndValue<String, CountPercentage>> buildUniqueInitialValues(
            List<IdAndValue<String, CountPercentage>> initialValues, boolean includePercentage) {

        CountPercentage count = includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();

        return initialValues.stream()
                .map(v -> IdAndValue.of("#" + v.getId(), count).setCompareId(Boolean.FALSE))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static DateStatusCount buildAggregatedTotalDateStatusCount(
            Map.Entry<DatePair, List<PerDateCount>> entry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            List<IdAndValue<String, CountPercentage>> uniqueInitialValues,
            boolean includePercentage,
            boolean includeTotal) {

        int expectedSize = Math.max(initialValues.size(), uniqueInitialValues.size());
        Map<String, Long> regularTotalMap = LinkedHashMap.newLinkedHashMap(expectedSize);
        Map<String, Long> uniqueTotalMap = LinkedHashMap.newLinkedHashMap(expectedSize);

        initialValues.forEach(v -> regularTotalMap.put(v.getId(), 0L));
        uniqueInitialValues.forEach(v -> uniqueTotalMap.put(v.getId(), 0L));

        for (PerDateCount pdc : entry.getValue()) {
            String mapValue = pdc.getMapValue();
            if (mapValue != null) {
                if (mapValue.startsWith("#")) {
                    uniqueTotalMap.merge(mapValue, pdc.getCount(), Long::sum);
                } else {
                    regularTotalMap.merge(mapValue, pdc.getCount(), Long::sum);
                }
            }
        }

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> regularTotalValueCounts =
                getTotalValueCounts(regularTotalMap, initialValues, includePercentage, includeTotal);

        StatusNameCount totalStatus = StatusNameCount.of(TOTAL, regularTotalValueCounts.getT2());

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> uniqueTotalValueCounts =
                getTotalValueCounts(uniqueTotalMap, uniqueInitialValues, includePercentage, includeTotal);

        StatusNameCount uniqueTotalStatus = StatusNameCount.of("#" + TOTAL, uniqueTotalValueCounts.getT2());

        List<StatusNameCount> statusCounts = new ArrayList<>(includeTotal ? 2 : 0);

        if (includeTotal) {
            statusCounts.add(totalStatus);
            statusCounts.add(uniqueTotalStatus);
        }

        if (includePercentage && includeTotal) addPercentage(statusCounts, true);

        return new DateStatusCount().setDatePair(entry.getKey()).setStatusCount(statusCounts);
    }

    private static <T extends BaseStatusCount<T>> List<T> addPercentage(List<T> statusCountList, boolean includeTotal) {

        if (statusCountList.isEmpty()) return statusCountList;

        T totalEntry = includeTotal
                ? statusCountList.stream()
                        .filter(statusCount -> TOTAL.equalsIgnoreCase(statusCount.getName()))
                        .findFirst()
                        .orElse(null)
                : null;

        if (includeTotal && totalEntry == null) return statusCountList;

        if (totalEntry == null) return statusCountList;

        long total = totalEntry.getPerCount() != null
                ? totalEntry.getPerCount().stream()
                        .mapToLong(pc -> pc.getValue().getCount().longValue())
                        .sum()
                : 0L;

        if (total <= 0) return statusCountList;

        statusCountList.forEach(statusCount -> {
            if (statusCount.getPerCount() != null)
                statusCount.getPerCount().stream()
                        .filter(pc -> pc.getValue() != null)
                        .forEach(pc -> pc.getValue().recalculatePercentage(total));
        });

        return statusCountList;
    }

    /* -----------------------------------------------------------------------
     * Value/group based reports (groupedId / groupedValue)
     * ----------------------------------------------------------------------- */

    public static <T extends PerCount<T>> Flux<StatusEntityCount> toStatusCountsGroupedIds(
            List<T> perCountList,
            List<IdAndValue<ULong, String>> groupedIdsObjectList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        if (includeNone && !includeTotal) return Flux.empty();
        if (perCountList.isEmpty() && !includeZero) return Flux.empty();

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage, includeTotal);

        if (includeNone)
            return getOnlyGroupedIdTotal(perCountList, initialValues, includePercentage)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedIds"));

        Map<ULong, String> allGroupedIds = IdAndValue.toMap(groupedIdsObjectList);

        Map<ULong, Map<String, Long>> grouped = groupByGroupedIdThenMapValue(perCountList);

        if (includeZero) addMissingIdsEntries(grouped, allGroupedIds.keySet(), initialValues);

        return convertGroupedIdToStatusIdCounts(grouped, allGroupedIds, initialValues, includePercentage)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedIds"));
    }

    // --- GroupedId report helpers ---

    private static <T extends PerCount<T>> Map<ULong, Map<String, Long>> groupByGroupedIdThenMapValue(
            List<T> perCountList) {
        return perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));
    }

    private static void addMissingIdsEntries(
            Map<ULong, Map<String, Long>> groupedIds,
            Set<ULong> allGroupedIds,
            List<IdAndValue<String, CountPercentage>> initialValues) {

        allGroupedIds.forEach(id -> {
            Map<String, Long> valueMap = groupedIds.computeIfAbsent(id, k -> new LinkedHashMap<>());
            initialValues.forEach(value -> valueMap.putIfAbsent(value.getId(), 0L));
        });
    }

    private static Flux<StatusEntityCount> convertGroupedIdToStatusIdCounts(
            Map<ULong, Map<String, Long>> groupedIdCounts,
            Map<ULong, String> objectNameMap,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Flux<Map.Entry<ULong, Map<String, Long>>> flux = Flux.fromIterable(groupedIdCounts.entrySet());

        if (groupedIdCounts.size() > PARALLEL_THRESHOLD)
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processGroupedIdEntry(entry, objectNameMap, initialValues, includePercentage))
                    .sequential();

        return flux.map(entry -> processGroupedIdEntry(entry, objectNameMap, initialValues, includePercentage));
    }

    private static StatusEntityCount processGroupedIdEntry(
            Map.Entry<ULong, Map<String, Long>> idEntry,
            Map<ULong, String> objectNameMap,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        ULong id = idEntry.getKey();
        String name = objectNameMap.get(id);

        boolean includeTotal = idEntry.getValue().containsKey(TOTAL);
        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> totalValueCounts =
                getTotalValueCounts(idEntry.getValue(), initialValues, includePercentage, includeTotal);

        return StatusEntityCount.of(id, name, totalValueCounts.getT2());
    }

    private static <T extends PerCount<T>> Flux<StatusEntityCount> getOnlyGroupedIdTotal(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues, boolean includePercentage) {

        Map<String, Long> totalMap = getOnlyTotalMap(perCountList, initialValues);

        return Flux.just(processGroupedIdEntry(
                new AbstractMap.SimpleEntry<>(TOTAL_ID, totalMap),
                Map.of(TOTAL_ID, TOTAL),
                initialValues,
                includePercentage));
    }

    public static <T extends PerCount<T>> Flux<StatusNameCount> toStatusCountsGroupedValue(
            List<T> perCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        if (includeNone && !includeTotal) return Flux.empty();
        if (perCountList.isEmpty() && !includeZero) return Flux.empty();

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage, includeTotal);

        if (includeNone)
            return getOnlyGroupedValueTotal(perCountList, initialValues, includePercentage)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"));

        Set<String> allGroupedValues = collectGroupedValues(perCountList);

        Map<String, Map<String, Long>> grouped = groupByGroupedValueThenMapValue(perCountList);

        if (includeZero) addMissingValuesEntries(grouped, allGroupedValues, initialValues);

        Flux<StatusNameCount> base = convertGroupedValueToStatusNameCounts(grouped, initialValues, includePercentage);

        return includeTotal
                ? appendOverallTotalStatusRow(base, initialValues, includePercentage)
                        .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"))
                : base.contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"));
    }

    // --- GroupedValue report helpers ---

    private static Set<String> collectGroupedValues(List<? extends PerCount<?>> perCountList) {
        return perCountList.stream().map(PerCount::getGroupedValue).collect(Collectors.toSet());
    }

    private static <T extends PerCount<T>> Map<String, Map<String, Long>> groupByGroupedValueThenMapValue(
            List<T> perCountList) {
        return perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));
    }

    private static void addMissingValuesEntries(
            Map<String, Map<String, Long>> groupedValues,
            Set<String> allGroupedValues,
            List<IdAndValue<String, CountPercentage>> initialValues) {

        allGroupedValues.forEach(name -> {
            Map<String, Long> valueMap = groupedValues.computeIfAbsent(name, k -> new LinkedHashMap<>());
            initialValues.forEach(value -> valueMap.putIfAbsent(value.getId(), 0L));
        });
    }

    private static Flux<StatusNameCount> convertGroupedValueToStatusNameCounts(
            Map<String, Map<String, Long>> groupedValueCounts,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Flux<Map.Entry<String, Map<String, Long>>> flux = Flux.fromIterable(groupedValueCounts.entrySet());

        if (groupedValueCounts.size() > PARALLEL_THRESHOLD)
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processGroupedValueEntry(entry, initialValues, includePercentage))
                    .sequential();

        return flux.map(entry -> processGroupedValueEntry(entry, initialValues, includePercentage));
    }

    private static StatusNameCount processGroupedValueEntry(
            Map.Entry<String, Map<String, Long>> valueEntry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        boolean includeTotal = valueEntry.getValue().containsKey(TOTAL);
        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> totalValueCounts =
                getTotalValueCounts(valueEntry.getValue(), initialValues, includePercentage, includeTotal);

        return StatusNameCount.of(valueEntry.getKey(), totalValueCounts.getT2());
    }

    private static <T extends PerCount<T>> Flux<StatusNameCount> getOnlyGroupedValueTotal(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues, boolean includePercentage) {

        Map<String, Long> totalMap = getOnlyTotalMap(perCountList, initialValues);

        return Flux.just(processGroupedValueEntry(
                new AbstractMap.SimpleEntry<>(TOTAL, totalMap), initialValues, includePercentage));
    }

    private static Flux<StatusNameCount> appendOverallTotalStatusRow(
            Flux<StatusNameCount> base,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        return base.collectList().flatMapMany(statusCounts -> {
            if (statusCounts.isEmpty()) return Flux.empty();

            Map<String, Long> aggregated = aggregatePerCountAcrossStatuses(statusCounts, initialValues);
            StatusNameCount totalStatus = StatusNameCount.of(TOTAL, toPerCountList(aggregated, includePercentage));

            List<StatusNameCount> result = new ArrayList<>(statusCounts.size() + 1);
            result.addAll(statusCounts);
            result.add(totalStatus);
            result.sort(statusNameTotalComparator);

            return Flux.fromIterable(result);
        });
    }

    private static Map<String, Long> aggregatePerCountAcrossStatuses(
            List<StatusNameCount> statusCounts, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> aggregated = initAggregatedMapFromInitialValues(initialValues);

        for (StatusNameCount statusCount : statusCounts) {
            List<IdAndValue<String, CountPercentage>> perCounts = statusCount.getPerCount();
            if (perCounts == null) continue;

            perCounts.stream()
                    .filter(Objects::nonNull)
                    .forEach(pc -> aggregated.merge(pc.getId(), safeCount(pc.getValue()), Long::sum));
        }

        return aggregated;
    }

    private static long safeCount(CountPercentage cp) {
        return (cp != null && cp.getCount() != null) ? cp.getCount().longValue() : 0L;
    }

    private static Map<String, Long> initAggregatedMapFromInitialValues(
            List<IdAndValue<String, CountPercentage>> initialValues) {
        Map<String, Long> aggregated = new LinkedHashMap<>();
        initialValues.stream()
                .map(IdAndValue::getId)
                .filter(k -> k != null && !TOTAL.equalsIgnoreCase(k))
                .forEach(k -> aggregated.put(k, 0L));
        return aggregated;
    }

    private static List<IdAndValue<String, CountPercentage>> toPerCountList(
            Map<String, Long> aggregated, boolean includePercentage) {

        long total = aggregated.values().stream().mapToLong(Long::longValue).sum();

        List<IdAndValue<String, CountPercentage>> result = new ArrayList<>(aggregated.size());

        Long totalValue = aggregated.get(TOTAL);
        if (totalValue != null)
            result.add(IdAndValue.of(
                    TOTAL,
                    includePercentage ? CountPercentage.of(totalValue, total) : CountPercentage.withCount(totalValue)));

        aggregated.entrySet().stream()
                .filter(e -> !TOTAL.equalsIgnoreCase(e.getKey()))
                .map(e -> IdAndValue.of(
                        e.getKey(),
                        includePercentage
                                ? CountPercentage.of(e.getValue(), total)
                                : CountPercentage.withCount(e.getValue())))
                .forEach(result::add);

        return result;
    }

    // --- Shared helpers for grouped reports ---

    private static <T extends PerCount<T>> List<IdAndValue<String, CountPercentage>> buildInitialValues(
            List<T> perValueCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includePercentage,
            boolean includeTotal) {

        CountPercentage count = includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();

        if (requiredValueList != null && !requiredValueList.isEmpty()) {
            List<IdAndValue<String, CountPercentage>> result = requiredValueList.stream()
                    .map(value -> IdAndValue.of(value.getValue(), count).setCompareId(Boolean.FALSE))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (includeTotal) {
                result.removeIf(v -> v != null && TOTAL.equalsIgnoreCase(v.getId()));
                result.addFirst(IdAndValue.of(TOTAL, count).setCompareId(Boolean.FALSE));
            }

            return result;
        }

        List<IdAndValue<String, CountPercentage>> result = perValueCountList.stream()
                .map(PerCount::getMapValue)
                .filter(mapValue -> mapValue == null || !mapValue.startsWith("#"))
                .filter(mapValue -> !TOTAL.equalsIgnoreCase(mapValue))
                .distinct()
                .map(mapValue -> IdAndValue.of(mapValue, count).setCompareId(Boolean.FALSE))
                .collect(Collectors.toCollection(ArrayList::new));

        if (includeTotal) result.addFirst(IdAndValue.of(TOTAL, count).setCompareId(Boolean.FALSE));

        return result;
    }

    private static <T extends PerCount<T>> Map<String, Long> getOnlyTotalMap(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = LinkedHashMap.newLinkedHashMap(initialValues.size());
        initialValues.forEach(v -> totalMap.put(v.getId(), 0L));

        perCountList.forEach(pvc -> totalMap.merge(pvc.getMapValue(), pvc.getCount(), Long::sum));

        return totalMap;
    }

    private static Tuple2<Long, List<IdAndValue<String, CountPercentage>>> getTotalValueCounts(
            Map<String, Long> values,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage,
            boolean includeTotal) {

        Long totalValue = values.get(TOTAL);
        boolean hasTotal = totalValue != null && includeTotal;

        return includePercentage && hasTotal
                ? getTotalValueCountsWithPercentage(values, initialValues, totalValue)
                : getTotalValueCountsWithoutPercentage(values, initialValues);
    }

    private static Tuple2<Long, List<IdAndValue<String, CountPercentage>>> getTotalValueCountsWithPercentage(
            Map<String, Long> values, List<IdAndValue<String, CountPercentage>> initialValues, Long totalValue) {

        long totalCount = totalValue;

        List<IdAndValue<String, CountPercentage>> valueCounts = new ArrayList<>(initialValues.size());

        for (IdAndValue<String, CountPercentage> initialValue : initialValues) {
            String valueKey = initialValue.getId();
            Long count = values.getOrDefault(valueKey, 0L);

            CountPercentage cp = CountPercentage.of(count, totalCount);
            valueCounts.add(IdAndValue.of(valueKey, cp));
        }

        return Tuples.of(totalCount, valueCounts);
    }

    private static Tuple2<Long, List<IdAndValue<String, CountPercentage>>> getTotalValueCountsWithoutPercentage(
            Map<String, Long> values, List<IdAndValue<String, CountPercentage>> initialValues) {

        List<IdAndValue<String, CountPercentage>> valueCounts = new ArrayList<>(initialValues.size());

        for (IdAndValue<String, CountPercentage> initialValue : initialValues) {
            String valueKey = initialValue.getId();
            Long count = values.getOrDefault(valueKey, 0L);

            CountPercentage cp = CountPercentage.withCount(count);
            valueCounts.add(IdAndValue.of(valueKey, cp));
        }

        return Tuples.of(0L, valueCounts);
    }

    /* -----------------------------------------------------------------------
     * Entity based reports
     * ----------------------------------------------------------------------- */

    public static Flux<EntityEntityCount> toEntityStageCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> innerEntityList,
            List<IdAndValue<ULong, String>> outerEntityList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeAllTotal) {

        if (perValueCountList.isEmpty() && !includeZero) return Flux.empty();

        Map<ULong, String> innerEntityMap = IdAndValue.toMap(innerEntityList);
        Map<ULong, String> outerEntityMap = IdAndValue.toMap(outerEntityList);

        Map<String, Map<ULong, Long>> grouped = groupByGroupedValueThenGroupedId(perValueCountList);

        return Flux.fromIterable(grouped.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .publishOn(Schedulers.boundedElastic())
                .map(entry -> buildAggregatedTotalEntityStatusCount(
                        entry, innerEntityMap, outerEntityMap, includePercentage, includeZero))
                .filter(e -> includeAllTotal || isTotalCountNonZero(e.getTotalCount()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toEntityStageCounts"));
    }

    // --- EntityStage report helpers ---

    private static Map<String, Map<ULong, Long>> groupByGroupedValueThenGroupedId(
            List<PerValueCount> perValueCountList) {
        return perValueCountList.stream()
                .collect(Collectors.groupingBy(
                        PerValueCount::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                PerValueCount::getGroupedId,
                                LinkedHashMap::new,
                                Collectors.summingLong(PerValueCount::getCount))));
    }

    private static EntityEntityCount buildAggregatedTotalEntityStatusCount(
            Map.Entry<String, Map<ULong, Long>> entry,
            Map<ULong, String> innerEntityMap,
            Map<ULong, String> outerEntityMap,
            boolean includePercentage,
            boolean includeZero) {

        String outerEntityIdStr = entry.getKey();
        ULong outerEntityId = ULongUtil.valueOf(outerEntityIdStr);
        String outerEntityName = outerEntityMap.getOrDefault(outerEntityId, outerEntityIdStr);

        Map<ULong, Long> innerEntityData = entry.getValue();

        if (includeZero)
            innerEntityMap.keySet().forEach(innerEntityId -> innerEntityData.putIfAbsent(innerEntityId, 0L));

        List<EntityCount> statusCounts = new ArrayList<>(innerEntityData.size());

        for (Map.Entry<ULong, Long> innerEntityEntry : innerEntityData.entrySet()) {
            ULong innerEntityId = innerEntityEntry.getKey();
            String innerEntityName = innerEntityMap.getOrDefault(innerEntityId, "Unknown");
            Long totalCount = innerEntityEntry.getValue();

            CountPercentage totalCountPercentage =
                    includePercentage ? CountPercentage.of(totalCount, 0.0) : CountPercentage.withCount(totalCount);

            statusCounts.add(EntityCount.of(innerEntityId, innerEntityName, totalCountPercentage));
        }

        return new EntityEntityCount(outerEntityId, outerEntityName, statusCounts, includePercentage);
    }

    public static Flux<EntityDateCount> toEntityDateCounts(
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> outerEntityList,
            BaseFilter.ReportOptions options) {

        if (perDateCountList.isEmpty() && !options.includeZero()) return Flux.empty();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(options.totalDatePair(), options.timePeriod(), perDateCountList);

        Map<ULong, String> outerEntityMap = IdAndValue.toMap(outerEntityList);

        int expectedSize = Math.max(outerEntityList.size(), perDateCountList.size() / 10);
        Map<ULong, NavigableMap<DatePair, Long>> grouped =
                LinkedHashMap.newLinkedHashMap((int) (expectedSize / 0.75f) + 1);

        accumulateEntityDateCounts(perDateCountList, datePairMap, grouped);

        if (options.includeZero() && !outerEntityList.isEmpty())
            outerEntityList.forEach(client -> grouped.computeIfAbsent(client.getId(), k -> new TreeMap<>()));

        return Flux.fromIterable(grouped.entrySet())
                .filter(entry -> options.includeZero() || !entry.getValue().isEmpty())
                .map(entry -> buildAggregatedTotalEntityDateCount(
                        entry, datePairMap, outerEntityMap, options.includePercentage(), options.includeZero()))
                .filter(e -> options.includeAllTotal() || isTotalCountNonZero(e.getTotalCount()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toEntityDateCounts"));
    }

    private static boolean isTotalCountNonZero(CountPercentage totalCount) {
        return totalCount != null
                && totalCount.getCount() != null
                && totalCount.getCount().longValue() != 0L;
    }

    // --- EntityDate report helpers ---

    private static void accumulateEntityDateCounts(
            List<PerDateCount> perDateCountList,
            NavigableMap<DatePair, List<PerDateCount>> datePairMap,
            Map<ULong, NavigableMap<DatePair, Long>> grouped) {

        for (PerDateCount pdc : perDateCountList) {
            String entityIdStr = pdc.getGroupedValue();
            if (entityIdStr == null) continue;

            ULong entityId = ULongUtil.valueOf(entityIdStr);
            DatePair datePair = DatePair.findContainingDate(pdc.getDate(), datePairMap);
            if (datePair != null)
                grouped.computeIfAbsent(entityId, k -> new TreeMap<>()).merge(datePair, pdc.getCount(), Long::sum);
        }
    }

    private static EntityDateCount buildAggregatedTotalEntityDateCount(
            Map.Entry<ULong, NavigableMap<DatePair, Long>> entry,
            NavigableMap<DatePair, List<PerDateCount>> datePairMap,
            Map<ULong, String> outerEntityMap,
            boolean includePercentage,
            boolean includeZero) {

        ULong outerEntityId = entry.getKey();
        String outerEntityName = outerEntityMap.getOrDefault(outerEntityId, outerEntityId.toString());

        NavigableMap<DatePair, Long> dateCounts = entry.getValue();

        if (includeZero) datePairMap.keySet().forEach(datePair -> dateCounts.putIfAbsent(datePair, 0L));

        List<DateCount> dateCountList = new ArrayList<>(dateCounts.size());

        for (Map.Entry<DatePair, Long> dateEntry : dateCounts.entrySet()) {
            DatePair datePair = dateEntry.getKey();
            Long totalCount = dateEntry.getValue();

            CountPercentage totalCountPercentage =
                    includePercentage ? CountPercentage.of(totalCount, 0.0) : CountPercentage.withCount(totalCount);

            dateCountList.add(DateCount.of(datePair, totalCountPercentage));
        }

        return new EntityDateCount(outerEntityId, outerEntityName, dateCountList, includePercentage);
    }
}
