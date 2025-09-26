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
            addTotalIdEntry(grouped, initialValues);
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

        if (includeTotal) addTotalNameEntry(grouped, initialValues);

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

    private static void addTotalIdEntry(
            Map<ULong, Map<String, Long>> grouped, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(value -> totalMap.put(value.getId(), 0L));

        for (Map<String, Long> valueMap : grouped.values())
            valueMap.forEach((key, value) -> totalMap.merge(key, value, Long::sum));

        grouped.put(TOTAL_ID, totalMap);
    }

    private static void addTotalNameEntry(
            Map<String, Map<String, Long>> grouped, List<IdAndValue<String, CountPercentage>> initialValues) {

        Map<String, Long> totalMap = new LinkedHashMap<>();
        initialValues.forEach(value -> totalMap.put(value.getId(), 0L));

        for (Map<String, Long> valueMap : grouped.values())
            valueMap.forEach((key, value) -> totalMap.merge(key, value, Long::sum));

        grouped.put(TOTAL, totalMap);
    }

    private static Flux<StatusEntityCount> convertIdGroupsToStatusIdCounts(
            Map<ULong, Map<String, Long>> groupedCounts,
            Map<ULong, String> objectNameMap,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        int dataSize = groupedCounts.size();
        boolean useParallel = dataSize > PARALLEL_THRESHOLD;

        Flux<Map.Entry<ULong, Map<String, Long>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (useParallel) {
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processIdEntry(entry, objectNameMap, initialValues, includePercentage))
                    .sequential();
        } else {
            return flux.map(entry -> processIdEntry(entry, objectNameMap, initialValues, includePercentage));
        }
    }

    private static Flux<StatusNameCount> convertGroupsNameToStatusNameCounts(
            Map<String, Map<String, Long>> groupedCounts,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        int dataSize = groupedCounts.size();
        boolean useParallel = dataSize > PARALLEL_THRESHOLD;

        Flux<Map.Entry<String, Map<String, Long>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (useParallel) {
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processNameEntry(entry, initialValues, includePercentage))
                    .sequential();
        } else {
            return flux.map(entry -> processNameEntry(entry, initialValues, includePercentage));
        }
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
        Map<String, Long> values = entry.getValue();

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

        return StatusEntityCount.of(id, name, CountPercentage.withCount(totalCount), valueCounts);
    }

    private static StatusNameCount processNameEntry(
            Map.Entry<String, Map<String, Long>> entry,
            List<IdAndValue<String, CountPercentage>> initialValues,
            boolean includePercentage) {

        String name = entry.getKey();
        Map<String, Long> values = entry.getValue();

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

        return StatusNameCount.of(name, CountPercentage.withCount(totalCount), valueCounts);
    }
}
