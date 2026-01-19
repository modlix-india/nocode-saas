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
import com.fincity.saas.entity.processor.analytics.model.base.BaseStatusCount;
import com.fincity.saas.entity.processor.analytics.model.base.PerCount;
import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.common.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.common.PerValueCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
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

    public static Flux<DateStatusCount> toDateStatusCounts( // NOSONAR
            DatePair totalDatePair,
            TimePeriod timePeriod,
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(totalDatePair, timePeriod, perDateCountList);

        if (requiredValueList == null)
            requiredValueList = perDateCountList.stream()
                    .map(perDateCount -> IdAndValue.of(ULong.MIN, perDateCount.getMapValue()))
                    .distinct()
                    .toList();

        Set<String> groupedValue = includeZero
                ? perDateCountList.stream().map(PerDateCount::getGroupedValue).collect(Collectors.toSet())
                : Set.of();

        List<IdAndValue<ULong, String>> finalRequired = requiredValueList;
        return Flux.fromIterable(datePairMap.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(entry -> {
                    String mapValue = finalRequired.getFirst().getValue();

                    List<PerDateCount> dateGroupedListWithZeros = includeZero
                            ? Stream.concat(
                                            entry.getValue().stream(),
                                            groupedValue.stream().map(value -> new PerDateCount()
                                                    .setCount(0L)
                                                    .setDate(entry.getKey().getFirst())
                                                    .setGroupedValue(value)
                                                    .setMapValue(mapValue)))
                                    .toList()
                            : entry.getValue();

                    return toStatusCountsGroupedValue(
                                    dateGroupedListWithZeros,
                                    finalRequired,
                                    includeZero,
                                    includePercentage,
                                    includeTotal,
                                    includeNone)
                            .collectList()
                            .map(statusCounts -> {
                                List<StatusNameCount> processed =
                                        includePercentage ? addPercentage(statusCounts, includeTotal) : statusCounts;
                                processed.sort(includeTotal ? statusNameTotalComparator : statusNameComparator);
                                return new DateStatusCount()
                                        .setDatePair(entry.getKey())
                                        .setStatusCount(processed);
                            });
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCounts"));
    }

    public static Flux<DateStatusCount> toDateStatusCountsAggregatedTotal(
            DatePair totalDatePair,
            TimePeriod timePeriod,
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        List<PerDateCount> totalEntries = includeTotal
                ? perDateCountList.stream()
                        .filter(pdc -> pdc.getDate() == null)
                        .toList()
                : List.of();

        List<PerDateCount> regularDateCountList = perDateCountList.stream()
                .filter(pdc -> pdc.getDate() != null)
                .toList();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(totalDatePair, timePeriod, regularDateCountList);

        if (includeTotal && !totalEntries.isEmpty()) {
	        datePairMap.keySet().forEach(datePair -> datePairMap.get(datePair).addAll(totalEntries));
        }

        if (requiredValueList == null)
            requiredValueList = perDateCountList.stream()
                    .map(perDateCount -> IdAndValue.of(ULong.MIN, perDateCount.getMapValue()))
                    .filter(idValue ->
                            idValue.getValue() == null || !idValue.getValue().startsWith("#"))
                    .distinct()
                    .toList();

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perDateCountList, requiredValueList, includePercentage, includeTotal);

        List<IdAndValue<String, CountPercentage>> uniqueInitialValues = initialValues.stream()
                .map(v -> {
                    CountPercentage count =
                            includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();
                    return IdAndValue.of("#" + v.getId(), count).setCompareId(Boolean.FALSE);
                })
                .collect(Collectors.toCollection(LinkedList::new));

        return Flux.fromIterable(datePairMap.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .publishOn(Schedulers.boundedElastic())
                .map(entry -> buildAggregatedTotalDateStatusCount(
                        entry, initialValues, uniqueInitialValues, includePercentage, includeTotal))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCountsAggregatedTotal"));
    }

    private static NavigableMap<DatePair, List<PerDateCount>> buildDatePairMap(
            DatePair totalDatePair, TimePeriod timePeriod, List<PerDateCount> perDateCountList) {

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                totalDatePair.toTimePeriodMap(timePeriod, LinkedList::new);

        for (PerDateCount pdc : perDateCountList) {
            DatePair datePair = DatePair.findContainingDate(pdc.getDate(), datePairMap);
            if (datePair != null) datePairMap.get(datePair).add(pdc);
        }

        return datePairMap;
    }

    private static DateStatusCount buildAggregatedTotalDateStatusCount(
            Map.Entry<DatePair, List<PerDateCount>> entry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            List<IdAndValue<String, CountPercentage>> uniqueInitialValues,
            boolean includePercentage,
            boolean includeTotal) {

        Map<String, Long> regularTotalMap = new LinkedHashMap<>();
        Map<String, Long> uniqueTotalMap = new LinkedHashMap<>();

        initialValues.forEach(v -> regularTotalMap.put(v.getId(), 0L));
        uniqueInitialValues.forEach(v -> uniqueTotalMap.put(v.getId(), 0L));

        for (PerDateCount pdc : entry.getValue()) {
            String mapValue = pdc.getMapValue();
            if (mapValue != null && mapValue.startsWith("#")) {
                uniqueTotalMap.merge(mapValue, pdc.getCount(), Long::sum);
            } else {
                regularTotalMap.merge(mapValue, pdc.getCount(), Long::sum);
            }
        }

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> regularTotalValueCounts =
                getTotalValueCounts(regularTotalMap, initialValues, includePercentage, includeTotal);

        StatusNameCount totalStatus = StatusNameCount.of(TOTAL, regularTotalValueCounts.getT2());

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> uniqueTotalValueCounts =
                getTotalValueCounts(uniqueTotalMap, uniqueInitialValues, includePercentage, includeTotal);

        StatusNameCount uniqueTotalStatus = StatusNameCount.of("#" + TOTAL, uniqueTotalValueCounts.getT2());

        List<StatusNameCount> statusCounts = new LinkedList<>();
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
            if (statusCount.getPerCount() != null) {
                statusCount.getPerCount().forEach(pc -> {
                    if (pc.getValue() != null) {
                        pc.getValue().recalculatePercentage(total);
                    }
                });
            }
        });

        return statusCountList;
    }

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

        Map<ULong, Map<String, Long>> grouped = perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));

        if (includeZero) addMissingIdsEntries(grouped, allGroupedIds.keySet(), initialValues);

        return convertGroupedIdToStatusIdCounts(grouped, allGroupedIds, initialValues, includePercentage)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedIds"));
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

        Set<String> allGroupedValues =
                perCountList.stream().map(PerCount::getGroupedValue).collect(Collectors.toSet());

        Map<String, Map<String, Long>> grouped = perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));

        if (includeZero) addMissingValuesEntries(grouped, allGroupedValues, initialValues);

        return convertGroupedValueToStatusNameCounts(grouped, initialValues, includePercentage)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"));
    }

    private static <T extends PerCount<T>> List<IdAndValue<String, CountPercentage>> buildInitialValues(
            List<T> perValueCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includePercentage,
            boolean includeTotal) {

        CountPercentage count = includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();

        if (requiredValueList != null && !requiredValueList.isEmpty()) {
            List<IdAndValue<String, CountPercentage>> result = requiredValueList.stream()
                    .map(value -> IdAndValue.of(value.getValue(), count).setCompareId(Boolean.FALSE))
                    .collect(Collectors.toCollection(LinkedList::new));

            if (includeTotal) result.add(IdAndValue.of(TOTAL, count).setCompareId(Boolean.FALSE));

            return result;
        }

        return perValueCountList.stream()
                .map(PerCount::getMapValue)
                .filter(mapValue -> mapValue == null || !mapValue.startsWith("#"))
                .filter(mapValue -> includeTotal || !TOTAL.equalsIgnoreCase(mapValue))
                .distinct()
                .map(mapValue -> IdAndValue.of(mapValue, count).setCompareId(Boolean.FALSE))
                .collect(Collectors.toCollection(LinkedList::new));
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

    private static void addMissingValuesEntries(
            Map<String, Map<String, Long>> groupedValues,
            Set<String> allGroupedValues,
            List<IdAndValue<String, CountPercentage>> initialValues) {

        allGroupedValues.forEach(name -> {
            Map<String, Long> valueMap = groupedValues.computeIfAbsent(name, k -> new LinkedHashMap<>());
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

    private static <T extends PerCount<T>> Flux<StatusEntityCount> getOnlyGroupedIdTotal(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues, boolean includePercentage) {

        Map<String, Long> totalMap = getOnlyTotalMap(perCountList, initialValues);

        return Flux.just(processGroupedIdEntry(
                new AbstractMap.SimpleEntry<>(TOTAL_ID, totalMap),
                Map.of(TOTAL_ID, TOTAL),
                initialValues,
                includePercentage));
    }

    private static <T extends PerCount<T>> Flux<StatusNameCount> getOnlyGroupedValueTotal(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues, boolean includePercentage) {

        Map<String, Long> totalMap = getOnlyTotalMap(perCountList, initialValues);

        return Flux.just(processGroupedValueEntry(
                new AbstractMap.SimpleEntry<>(TOTAL, totalMap), initialValues, includePercentage));
    }

    private static <T extends PerCount<T>> Map<String, Long> getOnlyTotalMap(
            List<T> perCountList, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(v -> totalMap.put(v.getId(), 0L));

        perCountList.forEach(pvc -> totalMap.merge(pvc.getMapValue(), pvc.getCount(), Long::sum));

        return totalMap;
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

    private static StatusNameCount processGroupedValueEntry(
            Map.Entry<String, Map<String, Long>> valueEntry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        boolean includeTotal = valueEntry.getValue().containsKey(TOTAL);
        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> totalValueCounts =
                getTotalValueCounts(valueEntry.getValue(), initialValues, includePercentage, includeTotal);

        return StatusNameCount.of(valueEntry.getKey(), totalValueCounts.getT2());
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

        List<IdAndValue<String, CountPercentage>> valueCounts = new LinkedList<>();

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

        List<IdAndValue<String, CountPercentage>> valueCounts = new LinkedList<>();

        for (IdAndValue<String, CountPercentage> initialValue : initialValues) {
            String valueKey = initialValue.getId();
            Long count = values.getOrDefault(valueKey, 0L);

            CountPercentage cp = CountPercentage.withCount(count);
            valueCounts.add(IdAndValue.of(valueKey, cp));
        }

        return Tuples.of(0L, valueCounts);
    }

    public static Flux<EntityEntityCount> toEntityStageCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> innerEntityList,
            List<IdAndValue<ULong, String>> outerEntityList,
            boolean includeZero,
            boolean includePercentage) {

        if (perValueCountList.isEmpty() && !includeZero) return Flux.empty();

        Map<ULong, String> innerEntityMap = IdAndValue.toMap(innerEntityList);
        Map<ULong, String> outerEntityMap = IdAndValue.toMap(outerEntityList);

        Map<String, Map<ULong, Long>> grouped = perValueCountList.stream()
                .collect(Collectors.groupingBy(
                        PerValueCount::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                PerValueCount::getGroupedId,
                                LinkedHashMap::new,
                                Collectors.summingLong(PerValueCount::getCount))));

        return Flux.fromIterable(grouped.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .publishOn(Schedulers.boundedElastic())
                .map(entry -> buildAggregatedTotalEntityStatusCount(
                        entry, innerEntityMap, outerEntityMap, includePercentage, includeZero))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toEntityStageCounts"));
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

        List<EntityCount> statusCounts = innerEntityData.entrySet().stream()
                .map(innerEntityEntry -> {
                    ULong innerEntityId = innerEntityEntry.getKey();
                    String innerEntityName = innerEntityMap.getOrDefault(innerEntityId, "Unknown");
                    Long totalCount = innerEntityEntry.getValue();

                    CountPercentage totalCountPercentage = includePercentage
                            ? CountPercentage.of(totalCount, 0.0)
                            : CountPercentage.withCount(totalCount);

                    return EntityCount.of(innerEntityId, innerEntityName, totalCountPercentage);
                })
                .toList();

        return new EntityEntityCount(outerEntityId, outerEntityName, statusCounts, includePercentage);
    }

    public static Flux<EntityDateCount> toEntityDateCounts(
            DatePair totalDatePair,
            TimePeriod timePeriod,
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> outerEntityList,
            boolean includeZero,
            boolean includePercentage) {

        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                buildDatePairMap(totalDatePair, timePeriod, perDateCountList);

        Map<ULong, String> outerEntityMap = IdAndValue.toMap(outerEntityList);

        int expectedSize = Math.max(outerEntityList.size(), perDateCountList.size() / 10);
        Map<ULong, NavigableMap<DatePair, Long>> grouped =
                LinkedHashMap.newLinkedHashMap((int) (expectedSize / 0.75f) + 1);

        for (PerDateCount pdc : perDateCountList) {
            String clientIdStr = pdc.getGroupedValue();
            if (clientIdStr == null) continue;

            ULong clientId = ULongUtil.valueOf(clientIdStr);
            DatePair datePair = DatePair.findContainingDate(pdc.getDate(), datePairMap);
            if (datePair != null)
                grouped.computeIfAbsent(clientId, k -> new TreeMap<>()).merge(datePair, pdc.getCount(), Long::sum);
        }

        if (includeZero && !outerEntityList.isEmpty())
            outerEntityList.forEach(client -> grouped.computeIfAbsent(client.getId(), k -> new TreeMap<>()));

        return Flux.fromIterable(grouped.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .map(entry -> buildAggregatedTotalEntityDateCount(
                        entry, datePairMap, outerEntityMap, includePercentage, includeZero))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toEntityDateCounts"));
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

        List<DateCount> dateCountList = dateCounts.entrySet().stream()
                .map(dateEntry -> {
                    DatePair datePair = dateEntry.getKey();
                    Long totalCount = dateEntry.getValue();

                    CountPercentage totalCountPercentage = includePercentage
                            ? CountPercentage.of(totalCount, 0.0)
                            : CountPercentage.withCount(totalCount);

                    return DateCount.of(datePair, totalCountPercentage);
                })
                .toList();

        return new EntityDateCount(outerEntityId, outerEntityName, dateCountList, includePercentage);
    }
}
