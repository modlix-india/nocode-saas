package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.model.CountPercentage;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@UtilityClass
public class ReportUtil {

    private static final String TOTAL_ID = "Total";

    public static Flux<StatusCount> toStatusCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        return toStatusCounts(perValueCountList, objectNameList, null, includeZero, includePercentage, includeTotal);
    }

    public static Flux<StatusCount> toStatusCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includeZero,
            boolean includePercentage,
            boolean includeTotal) {

        if (perValueCountList.isEmpty() && !includeZero) return Flux.empty();

        Map<ULong, String> objectNameMap = IdAndValue.toMap(objectNameList);

        Map<String, CountPercentage> initialValueMap =
                buildInitialValueMap(perValueCountList, requiredValueList, includePercentage);

        Map<ULong, List<PerValueCount>> grouped = perValueCountList.stream()
                .collect(Collectors.groupingBy(
                        PerValueCount::getId,
                        () -> LinkedHashMap.newLinkedHashMap(objectNameList.size()),
                        Collectors.toList()));

        if (includeZero) addMissingEntries(grouped, objectNameMap.keySet(), initialValueMap);

        if (includeTotal) {
            addTotalEntry(grouped, initialValueMap);
            objectNameMap.put(ULong.MIN, TOTAL_ID);
        }

        return convertGroupsToStatusCounts(grouped, objectNameMap, initialValueMap, includePercentage);
    }

    private static Map<String, CountPercentage> buildInitialValueMap(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> requiredValueList,
            boolean includePercentage) {

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
                        PerValueCount::getValue,
                        value -> count,
                        (a, b) -> b,
                        () -> LinkedHashMap.newLinkedHashMap(perValueCountList.size())));
    }

    private static void addMissingEntries(
            Map<ULong, List<PerValueCount>> grouped, Set<ULong> allIds, Map<String, CountPercentage> initialValueMap) {
        allIds.forEach(id -> grouped.computeIfAbsent(id, k -> initialValueMap.keySet().stream()
                .map(value -> new PerValueCount(k, value, 0L))
                .toList()));
    }

    private static void addTotalEntry(
            Map<ULong, List<PerValueCount>> grouped, Map<String, CountPercentage> initialValueMap) {

        Map<String, Long> valueSums = new LinkedHashMap<>();
        initialValueMap.keySet().forEach(k -> valueSums.put(k, 0L));

        grouped.values()
                .forEach(list -> list.forEach(pvc -> valueSums.merge(pvc.getValue(), pvc.getCount(), Long::sum)));

        ULong totalId = ULong.valueOf(0L);
        List<PerValueCount> totalList = initialValueMap.keySet().stream()
                .map(v -> new PerValueCount(totalId, v, valueSums.getOrDefault(v, 0L)))
                .toList();

        grouped.put(totalId, totalList);
    }

    private static Flux<StatusCount> convertGroupsToStatusCounts(
            Map<ULong, List<PerValueCount>> groupedCounts,
            Map<ULong, String> objectNameMap,
            Map<String, CountPercentage> initialValueMap,
            boolean includePercentage) {

        int dataSize = groupedCounts.size();
        boolean useParallel = dataSize > 100;

        Flux<Map.Entry<ULong, List<PerValueCount>>> flux = Flux.fromIterable(groupedCounts.entrySet());

        if (useParallel) {
            return flux.parallel()
                    .runOn(Schedulers.parallel())
                    .map(entry -> processEntry(entry, objectNameMap, initialValueMap, includePercentage))
                    .sequential();
        } else {
            return flux.map(entry -> processEntry(entry, objectNameMap, initialValueMap, includePercentage));
        }
    }

    private static StatusCount processEntry(
            Map.Entry<ULong, List<PerValueCount>> entry,
            Map<ULong, String> objectNameMap,
            Map<String, CountPercentage> initialValueMap,
            boolean includePercentage) {

        ULong id = entry.getKey();
        List<PerValueCount> pvcList = entry.getValue();
        String objectName = objectNameMap.get(id);

        Map<String, CountPercentage> valueCounts = new LinkedHashMap<>(initialValueMap);

        if (pvcList.isEmpty())
            return StatusCount.of(
                    id,
                    objectName,
                    includePercentage ? CountPercentage.zero() : CountPercentage.zeroNoPercent(),
                    valueCounts);

        Long totalCount = 0L;

        if (includePercentage) {
            totalCount = pvcList.stream().mapToLong(PerValueCount::getCount).sum();

            for (PerValueCount pvc : pvcList) {
                valueCounts.put(pvc.getValue(), CountPercentage.of(pvc.getCount(), totalCount, 2));
            }
        } else {
            for (PerValueCount pvc : pvcList) {
                valueCounts.put(pvc.getValue(), CountPercentage.withCount(pvc.getCount()));
                totalCount += pvc.getCount();
            }
        }

        return StatusCount.of(id, objectName, CountPercentage.withCount(totalCount), valueCounts);
    }
}
