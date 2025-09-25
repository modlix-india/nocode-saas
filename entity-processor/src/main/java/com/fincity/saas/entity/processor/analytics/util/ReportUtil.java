package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.StatusNameCount;
import com.fincity.saas.entity.processor.analytics.model.base.PerCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@UtilityClass
public class ReportUtil {

    private static final String TOTAL = "Total";
    private static final ULong TOTAL_ID = ULong.MIN;

    public static Flux<StatusEntityCount> toStatusCountsGroupedIds(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        return toStatusCountsGroupedIds(
                perValueCountList, objectNameList, null, includeZero, includePercentage, includeTotal);
    }

    public static Flux<StatusEntityCount> toStatusCountsGroupedIds(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> groupedIdsObjectList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        if (perValueCountList.isEmpty() && !includeZero) return Flux.empty();

        Map<ULong, String> groupedIdsObjectMap = IdAndValue.toMap(groupedIdsObjectList);

        Map<String, CountPercentage> initialValueMap =
                buildInitialValueMap(perValueCountList, requiredValueList, includePercentage);

        Map<ULong, List<PerValueCount>> grouped = perValueCountList.stream()
                .collect(Collectors.groupingBy(
                        PerValueCount::getGroupedId,
                        () -> LinkedHashMap.newLinkedHashMap(groupedIdsObjectList.size()),
                        Collectors.toList()));

        if (includeZero) addMissingIdsEntries(grouped, groupedIdsObjectMap.keySet(), initialValueMap);

        if (includeTotal) {
            addTotalIdEntry(grouped, initialValueMap);
            groupedIdsObjectMap.put(ULong.MIN, TOTAL);
        }

        return convertIdGroupsToStatusIdCounts(grouped, groupedIdsObjectMap, initialValueMap, includePercentage);
    }

    public static Flux<DateStatusCount> toDateStatusCounts(
            DatePair totalDatePair,
            TimePeriod timePeriod,
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        NavigableMap<DatePair, List<PerDateCount>> datePairMap =
                totalDatePair.toTimePeriodMap(timePeriod, new ArrayList<>());

        for (PerDateCount pvc : perDateCountList) {
            DatePair datePair = DatePair.findContainingDate(pvc.getDate(), datePairMap);
            if (datePair != null) {
                datePairMap.computeIfAbsent(datePair, k -> new ArrayList<>()).add(pvc);
            }
        }

        Iterable<Map.Entry<DatePair, List<PerDateCount>>> iterable = datePairMap.entrySet();

        return Flux.fromIterable(iterable)
                .filter(entry -> includeZero
                        || (entry.getValue() != null && !entry.getValue().isEmpty()))
                .publishOn(Schedulers.boundedElastic())
                .map(entry -> {
                    List<PerDateCount> list = entry.getValue();

                    List<StatusNameCount> statusCounts = toStatusCountsGroupedName(
                                    list, requiredValueList, includeZero, includePercentage, includeTotal)
                            .collectList()
                            .blockOptional()
                            .orElseGet(Collections::emptyList);

                    return new DateStatusCount().setDatePair(entry.getKey()).setStatusCount(statusCounts);
                });
    }

    public static Flux<StatusNameCount> toStatusCountsGroupedName(
            List<PerDateCount> perDateCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        if (perDateCountList.isEmpty() && !includeZero) return Flux.empty();

        Set<String> objectNameSet =
                perDateCountList.stream().map(PerCount::getGroupedValue).collect(Collectors.toSet());

        Map<String, CountPercentage> initialValueMap =
                buildInitialValueMap(perDateCountList, requiredValueList, includePercentage);

        Map<String, List<PerDateCount>> grouped = perDateCountList.stream()
                .collect(Collectors.groupingBy(
                        PerDateCount::getGroupedValue,
                        () -> LinkedHashMap.newLinkedHashMap(objectNameSet.size()),
                        Collectors.toList()));

        if (includeZero) addMissingNameEntries(grouped, objectNameSet, initialValueMap);

        if (includeTotal) addTotalNameEntry(grouped, initialValueMap);

        return convertGroupsNameToStatusNameCounts(grouped, initialValueMap, includePercentage);
    }

    private static <T extends PerCount<T>> Map<String, CountPercentage> buildInitialValueMap(
            List<T> perValueCountList, List<IdAndValue<ULong, String>> requiredValueList, boolean includePercentage) {

        CountPercentage count = includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent();

        if (requiredValueList != null && !requiredValueList.isEmpty())
            return requiredValueList.stream()
                    .collect(Collectors.toMap(
                            IdAndValue::getValue,
                            value -> count,
                            (a, b) -> b,
                            () -> LinkedHashMap.newLinkedHashMap(requiredValueList.size())));

        return perValueCountList.stream()
                .distinct()
                .collect(Collectors.toMap(
                        PerCount::getMapValue,
                        value -> count,
                        (a, b) -> b,
                        () -> LinkedHashMap.newLinkedHashMap(perValueCountList.size())));
    }

    private static <T extends PerCount<T>> void addMissingIdsEntries(
            Map<ULong, List<T>> grouped, Set<ULong> allGroupedIds, Map<String, CountPercentage> initialValueMap) {
        allGroupedIds.forEach(id -> grouped.computeIfAbsent(id, k -> initialValueMap.keySet().stream()
                .map(value -> new PerCount<T>() {}.create(k, value, 0L))
                .toList()));
    }

    private static <T extends PerCount<T>> void addMissingNameEntries(
            Map<String, List<T>> grouped, Set<String> allGroupedName, Map<String, CountPercentage> initialValueMap) {
        allGroupedName.forEach(name -> grouped.computeIfAbsent(name, k -> initialValueMap.keySet().stream()
                .map(value -> new PerCount<T>() {}.create(k, value, 0L))
                .toList()));
    }

    private static <T extends PerCount<T>> void addTotalIdEntry(
            Map<ULong, List<T>> grouped, Map<String, CountPercentage> initialValueMap) {

        Map<String, Long> valueSums = new LinkedHashMap<>();
        initialValueMap.keySet().forEach(k -> valueSums.put(k, 0L));

        for (List<T> list : grouped.values()) {
            list.forEach(pc -> valueSums.merge(pc.getMapValue(), pc.getCount(), Long::sum));
        }

        List<T> totalList = initialValueMap.keySet().stream()
                .map(v -> new PerCount<T>() {}.create(TOTAL_ID, v, valueSums.getOrDefault(v, 0L)))
                .toList();

        grouped.put(TOTAL_ID, totalList);
    }

    private static <T extends PerCount<T>> void addTotalNameEntry(
            Map<String, List<T>> grouped, Map<String, CountPercentage> initialValueMap) {

        Map<String, Long> valueSums = new LinkedHashMap<>();
        initialValueMap.keySet().forEach(k -> valueSums.put(k, 0L));

        for (List<T> list : grouped.values()) {
            list.forEach(pc -> valueSums.merge(pc.getMapValue(), pc.getCount(), Long::sum));
        }

        List<T> totalList = initialValueMap.keySet().stream()
                .map(v -> new PerCount<T>() {}.create(TOTAL, v, valueSums.getOrDefault(v, 0L)))
                .toList();

        grouped.put(TOTAL, totalList);
    }

    private static <T extends PerCount<T>> Flux<StatusEntityCount> convertIdGroupsToStatusIdCounts(
            Map<ULong, List<T>> groupedCounts,
            Map<ULong, String> objectNameMap,
            Map<String, CountPercentage> initialValueMap,
            boolean includePercentage) {

        int dataSize = groupedCounts.size();
        boolean useParallel = dataSize > 100;

        Flux<Map.Entry<ULong, List<T>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (useParallel) {
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processIdEntry(entry, objectNameMap, initialValueMap, includePercentage))
                    .sequential();
        } else {
            return flux.map(entry -> processIdEntry(entry, objectNameMap, initialValueMap, includePercentage));
        }
    }

    private static <T extends PerCount<T>> Flux<StatusNameCount> convertGroupsNameToStatusNameCounts(
            Map<String, List<T>> groupedCounts,
            Map<String, CountPercentage> initialValueMap,
            boolean includePercentage) {

        int dataSize = groupedCounts.size();
        boolean useParallel = dataSize > 100;

        Flux<Map.Entry<String, List<T>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (useParallel) {
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processNameEntry(entry, initialValueMap, includePercentage))
                    .sequential();
        } else {
            return flux.map(entry -> processNameEntry(entry, initialValueMap, includePercentage));
        }
    }

    private static <T extends PerCount<T>> StatusEntityCount processIdEntry(
            Map.Entry<ULong, List<T>> entry,
            Map<ULong, String> objectNameMap,
            Map<String, CountPercentage> initialValueMap,
            boolean includePercentage) {

        ULong id = entry.getKey();
        List<T> pcList = entry.getValue();
        String objectName = objectNameMap.get(id);

        Map<String, CountPercentage> valueCounts = CloneUtil.cloneMapObject(initialValueMap);

        if (pcList.isEmpty())
            return StatusEntityCount.of(
                    id,
                    objectName,
                    includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent(),
                    valueCounts);

        Long totalCount = 0L;

        for (T pc : pcList) {
            totalCount += pc.getCount();
            valueCounts
                    .computeIfAbsent(pc.getMapValue(), k -> CountPercentage.withCount(0))
                    .addCount(pc.getCount());
        }

        if (includePercentage) {
            for (T pc : pcList) {
                CountPercentage cp = valueCounts.get(pc.getMapValue());
                if (cp != null) cp.recalculatePercentage(totalCount, 2);
            }
        }

        return StatusEntityCount.of(id, objectName, CountPercentage.withCount(totalCount), valueCounts);
    }

    private static <T extends PerCount<T>> StatusNameCount processNameEntry(
            Map.Entry<String, List<T>> entry, Map<String, CountPercentage> initialValueMap, boolean includePercentage) {

        String name = entry.getKey();
        List<T> pcList = entry.getValue();

        Map<String, CountPercentage> valueCounts = CloneUtil.cloneMapObject(initialValueMap);

        if (pcList.isEmpty())
            return StatusNameCount.of(
                    name, includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent(), valueCounts);

        long totalCount = 0L;
        for (T pc : pcList) {
            totalCount += pc.getCount();
            valueCounts
                    .computeIfAbsent(pc.getMapValue(), k -> CountPercentage.withCount(0))
                    .addCount(pc.getCount());
        }

        if (includePercentage) {
            for (T pc : pcList) {
                CountPercentage cp = valueCounts.get(pc.getMapValue());
                if (cp != null) cp.recalculatePercentage(totalCount, 2);
            }
        }

        return StatusNameCount.of(name, CountPercentage.withCount(totalCount), valueCounts);
    }
}
