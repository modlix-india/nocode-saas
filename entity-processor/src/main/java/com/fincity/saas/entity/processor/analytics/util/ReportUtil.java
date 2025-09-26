package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.StatusNameCount;
import com.fincity.saas.entity.processor.analytics.model.base.PerCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
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

    public static Flux<DateStatusCount> toDateStatusCounts(
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
                totalDatePair.toTimePeriodMap(timePeriod, new LinkedList<>());

        for (PerDateCount pdc : perDateCountList) {
            DatePair datePair = DatePair.findContainingDate(pdc.getDate(), datePairMap);
            if (datePair != null) datePairMap.get(datePair).add(pdc);
        }

        return Flux.fromIterable(datePairMap.entrySet())
                .filter(entry -> includeZero || !entry.getValue().isEmpty())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(entry -> {
                    List<PerDateCount> dateGroupedList = entry.getValue();

                    return toStatusCountsGroupedValue(
                                    dateGroupedList,
                                    requiredValueList,
                                    includeZero,
                                    includePercentage,
                                    includeTotal,
                                    includeNone)
                            .collectList()
                            .map(statusCounts -> new DateStatusCount()
                                    .setDatePair(entry.getKey())
                                    .setStatusCount(statusCounts));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toDateStatusCounts"));
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

        if (includeNone)
            return getOnlyGroupedIdTotal(perCountList, requiredValueList, includePercentage)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedIds"));

        Map<ULong, String> allGroupedIds = IdAndValue.toMap(groupedIdsObjectList);

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

        Map<ULong, Map<String, Long>> grouped = perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));

        if (includeZero) addMissingIdsEntries(grouped, allGroupedIds.keySet(), initialValues);

        if (includeTotal) {
            grouped.put(TOTAL_ID, getTotalMap(grouped.values(), initialValues));
            allGroupedIds.put(TOTAL_ID, TOTAL);
        }

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

        if (includeNone)
            return getOnlyGroupedValueTotal(perCountList, requiredValueList, includePercentage)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"));

        Set<String> allGroupedValues =
                perCountList.stream().map(PerCount::getGroupedValue).collect(Collectors.toSet());

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

        Map<String, Map<String, Long>> grouped = perCountList.stream()
                .collect(Collectors.groupingBy(
                        T::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(T::getMapValue, Collectors.summingLong(T::getCount))));

        if (includeZero) addMissingValuesEntries(grouped, allGroupedValues, initialValues);

        if (includeTotal) grouped.put(TOTAL, getTotalMap(grouped.values(), initialValues));

        return convertGroupedValueToStatusNameCounts(grouped, initialValues, includePercentage)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ReportUtil.toStatusCountsGroupedValue"));
    }

    private static <T extends PerCount<T>> List<IdAndValue<String, CountPercentage>> buildInitialValues(
            List<T> perValueCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        CountPercentage count = includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();

        if (requiredValueList != null && !requiredValueList.isEmpty())
            return requiredValueList.stream()
                    .map(value -> IdAndValue.of(value.getValue(), count).setCompareId(Boolean.FALSE))
                    .collect(Collectors.toCollection(LinkedList::new));

        if (perValueCountList.isEmpty()) return new LinkedList<>();

        return perValueCountList.stream()
                .map(PerCount::getMapValue)
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

    private static Map<String, Long> getTotalMap(
            Collection<Map<String, Long>> groupedSet, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(value -> totalMap.put(value.getId(), 0L));

        for (Map<String, Long> valueMap : groupedSet)
            valueMap.forEach((key, value) -> totalMap.merge(key, value, Long::sum));

        return totalMap;
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
            List<T> perCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

        Map<String, Long> totalMap = getOnlyTotalMap(perCountList, initialValues);

        return Flux.just(processGroupedIdEntry(
                new AbstractMap.SimpleEntry<>(TOTAL_ID, totalMap),
                Map.of(TOTAL_ID, TOTAL),
                initialValues,
                includePercentage));
    }

    private static <T extends PerCount<T>> Flux<StatusNameCount> getOnlyGroupedValueTotal(
            List<T> perCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

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

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> totalValueCounts =
                getTotalValueCounts(idEntry.getValue(), initialValues, includePercentage);

        return StatusEntityCount.of(
                id, name, CountPercentage.withCount(totalValueCounts.getT1()), totalValueCounts.getT2());
    }

    private static StatusNameCount processGroupedValueEntry(
            Map.Entry<String, Map<String, Long>> valueEntry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> totalValueCounts =
                getTotalValueCounts(valueEntry.getValue(), initialValues, includePercentage);

        return StatusNameCount.of(
                valueEntry.getKey(), CountPercentage.withCount(totalValueCounts.getT1()), totalValueCounts.getT2());
    }

    private static Tuple2<Long, List<IdAndValue<String, CountPercentage>>> getTotalValueCounts(
            Map<String, Long> values,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        long totalCount = values.values().stream().mapToLong(Long::longValue).sum();

        List<IdAndValue<String, CountPercentage>> valueCounts = new ArrayList<>(initialValues.size());

        for (IdAndValue<String, CountPercentage> initialValue : initialValues) {
            String valueKey = initialValue.getId();
            Long count = values.getOrDefault(valueKey, 0L);

            CountPercentage cp = includePercentage && totalCount > 0
                    ? CountPercentage.of(count, totalCount)
                    : CountPercentage.withCount(count);

            valueCounts.add(IdAndValue.of(valueKey, cp));
        }

        return Tuples.of(totalCount, valueCounts);
    }
}
