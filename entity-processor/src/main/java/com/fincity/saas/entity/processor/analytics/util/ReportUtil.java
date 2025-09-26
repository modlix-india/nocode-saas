package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@UtilityClass
public class ReportUtil {

    private static final String TOTAL = "Total";
    private static final ULong TOTAL_ID = ULong.MIN;
    private static final int PARALLEL_THRESHOLD = Math.max(100, ForkJoinPool.getCommonPoolParallelism() * 4);

    public static Flux<StatusEntityCount> toStatusCountsGroupedIds(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        return toStatusCountsGroupedIds(
                perValueCountList, objectNameList, null, includeZero, includePercentage, includeTotal, includeNone);
    }

    public static Flux<StatusEntityCount> toStatusCountsGroupedIds(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> groupedIdsObjectList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        if (includeNone && !includeTotal) return Flux.empty();
        if (perValueCountList.isEmpty() && !includeZero) return Flux.empty();

        if (includeNone) return onlyTotalId(perValueCountList, requiredValueList, includePercentage);

        Map<ULong, String> groupedIdsObjectMap = IdAndValue.toMap(groupedIdsObjectList);

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perValueCountList, requiredValueList, includePercentage);

        Map<ULong, Map<String, Long>> grouped = perValueCountList.stream()
                .collect(Collectors.groupingBy(
                        PerValueCount::getGroupedId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                PerValueCount::getMapValue, Collectors.summingLong(PerValueCount::getCount))));

        if (includeZero) addMissingIdsEntries(grouped, groupedIdsObjectMap.keySet(), initialValues);

        if (includeTotal) {
            grouped.put(TOTAL_ID, getTotalMap(grouped.values(), initialValues));
            groupedIdsObjectMap.put(TOTAL_ID, TOTAL);
        }

        return convertIdGroupsToStatusIdCounts(grouped, groupedIdsObjectMap, initialValues, includePercentage);
    }

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

                    return toStatusCountsGroupedName(
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
                });
    }

    public static Flux<StatusNameCount> toStatusCountsGroupedName(
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal,
            boolean includeNone) {

        if (includeNone && !includeTotal) return Flux.empty();
        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        if (includeNone) return onlyTotalName(perDateCountList, requiredValueList, includePercentage);

        Set<String> objectNameSet =
                perDateCountList.stream().map(PerCount::getGroupedValue).collect(Collectors.toSet());

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perDateCountList, requiredValueList, includePercentage);

        Map<String, Map<String, Long>> grouped = perDateCountList.stream()
                .collect(Collectors.groupingBy(
                        PerDateCount::getGroupedValue,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                PerDateCount::getMapValue, Collectors.summingLong(PerDateCount::getCount))));

        if (includeZero) addMissingNameEntries(grouped, objectNameSet, initialValues);

        if (includeTotal) grouped.put(TOTAL, getTotalMap(grouped.values(), initialValues));

        return convertGroupsNameToStatusNameCounts(grouped, initialValues, includePercentage);
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
            Map<ULong, Map<String, Long>> grouped,
            Set<ULong> allGroupedIds,
            List<IdAndValue<String, CountPercentage>> initialValues) {

        allGroupedIds.forEach(id -> {
            Map<String, Long> valueMap = grouped.computeIfAbsent(id, k -> new LinkedHashMap<>());
            initialValues.forEach(value -> valueMap.putIfAbsent(value.getId(), 0L));
        });
    }

    private static void addMissingNameEntries(
            Map<String, Map<String, Long>> grouped,
            Set<String> allGroupedNames,
            List<IdAndValue<String, CountPercentage>> initialValues) {

        allGroupedNames.forEach(name -> {
            Map<String, Long> valueMap = grouped.computeIfAbsent(name, k -> new LinkedHashMap<>());
            initialValues.forEach(value -> valueMap.putIfAbsent(value.getId(), 0L));
        });
    }

    private static Map<String, Long> getTotalMap(
            Collection<Map<String, Long>> groupedValues, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(value -> totalMap.put(value.getId(), 0L));

        for (Map<String, Long> valueMap : groupedValues)
            valueMap.forEach((key, value) -> totalMap.merge(key, value, Long::sum));

        return totalMap;
    }

    private static Flux<StatusEntityCount> convertIdGroupsToStatusIdCounts(
            Map<ULong, Map<String, Long>> groupedCounts,
            Map<ULong, String> objectNameMap,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Flux<Map.Entry<ULong, Map<String, Long>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (groupedCounts.size() > PARALLEL_THRESHOLD)
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processIdEntry(entry, objectNameMap, initialValues, includePercentage))
                    .sequential();

        return flux.map(entry -> processIdEntry(entry, objectNameMap, initialValues, includePercentage));
    }

    private static Flux<StatusNameCount> convertGroupsNameToStatusNameCounts(
            Map<String, Map<String, Long>> groupedCounts,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Flux<Map.Entry<String, Map<String, Long>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (groupedCounts.size() > PARALLEL_THRESHOLD)
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processNameEntry(entry, initialValues, includePercentage))
                    .sequential();

        return flux.map(entry -> processNameEntry(entry, initialValues, includePercentage));
    }

    private static <T extends PerCount<T>> Flux<StatusEntityCount> onlyTotalId(
            List<T> perCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(v -> totalMap.put(v.getId(), 0L));

        perCountList.forEach(pvc -> totalMap.merge(pvc.getMapValue(), pvc.getCount(), Long::sum));

        return Flux.just(processIdEntry(
                new AbstractMap.SimpleEntry<>(TOTAL_ID, totalMap),
                Map.of(TOTAL_ID, TOTAL),
                initialValues,
                includePercentage));
    }

    private static <T extends PerCount<T>> Flux<StatusNameCount> onlyTotalName(
            List<T> perCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        List<IdAndValue<String, CountPercentage>> initialValues =
                buildInitialValues(perCountList, requiredValueList, includePercentage);

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(v -> totalMap.put(v.getId(), 0L));

        perCountList.forEach(pvc -> totalMap.merge(pvc.getMapValue(), pvc.getCount(), Long::sum));

        return Flux.just(
                processNameEntry(new AbstractMap.SimpleEntry<>(TOTAL, totalMap), initialValues, includePercentage));
    }

    private static StatusEntityCount processIdEntry(
            Map.Entry<ULong, Map<String, Long>> entry,
            Map<ULong, String> objectNameMap,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        ULong id = entry.getKey();
        String name = objectNameMap.get(id);

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> tuple =
                getTotalValueCounts(entry.getValue(), initialValues, includePercentage);

        return StatusEntityCount.of(id, name, CountPercentage.withCount(tuple.getT1()), tuple.getT2());
    }

    private static StatusNameCount processNameEntry(
            Map.Entry<String, Map<String, Long>> entry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        Tuple2<Long, List<IdAndValue<String, CountPercentage>>> tuple =
                getTotalValueCounts(entry.getValue(), initialValues, includePercentage);

        return StatusNameCount.of(entry.getKey(), CountPercentage.withCount(tuple.getT1()), tuple.getT2());
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
