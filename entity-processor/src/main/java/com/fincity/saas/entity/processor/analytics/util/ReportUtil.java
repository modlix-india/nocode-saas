package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@UtilityClass
public class ReportUtil {

    public static Flux<StatusCount> toStatusCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            List<String> requiredValueList,
            Boolean includeZero) {

        return toStatusCounts(perValueCountList, objectNameList, requiredValueList, Function.identity(), includeZero);
    }

    public static <T> Flux<StatusCount> toStatusCounts(
            List<PerValueCount> perValueCountList,
            List<IdAndValue<ULong, String>> objectNameList,
            List<T> requiredValueList,
            Function<T, String> getNameFunction,
            Boolean includeZero) {

        Map<String, Long> initialValueMap = requiredValueList.stream()
                .collect(Collectors.toMap(
                        getNameFunction, v -> 0L, (existing, replacement) -> existing, LinkedHashMap::new));

        Map<ULong, List<PerValueCount>> grouped = perValueCountList.stream()
                .collect(Collectors.groupingBy(PerValueCount::getId, LinkedHashMap::new, Collectors.toList()));

        return Boolean.TRUE.equals(includeZero)
                ? addZeroCountsAndConvert(grouped, IdAndValue.toMap(objectNameList), initialValueMap)
                : convertGroupsToStatusCounts(grouped, IdAndValue.toMap(objectNameList), initialValueMap);
    }

    private static Flux<StatusCount> addZeroCountsAndConvert(
            Map<ULong, List<PerValueCount>> grouped,
            Map<ULong, String> objectNameMap,
            Map<String, Long> initialValueMap) {

        Map<ULong, List<PerValueCount>> zeroCountsMap = objectNameMap.keySet().stream()
                .filter(id -> !grouped.containsKey(id))
                .collect(Collectors.toMap(
                        id -> id,
                        id -> initialValueMap.keySet().stream()
                                .map(value -> new PerValueCount(id, value, 0L))
                                .toList(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        if (zeroCountsMap.isEmpty()) return convertGroupsToStatusCounts(grouped, objectNameMap, initialValueMap);

        Map<ULong, List<PerValueCount>> finalGrouped = new LinkedHashMap<>(grouped);
        finalGrouped.putAll(zeroCountsMap);

        return convertGroupsToStatusCounts(finalGrouped, objectNameMap, initialValueMap);
    }

    private static Flux<StatusCount> convertGroupsToStatusCounts(
            Map<ULong, List<PerValueCount>> groupedCounts,
            Map<ULong, String> objectNameMap,
            Map<String, Long> initialValueMap) {

        return Flux.fromIterable(groupedCounts.entrySet())
                .parallel()
                .runOn(Schedulers.parallel())
                .map(entry -> {
                    ULong id = entry.getKey();
                    List<PerValueCount> pvcList = entry.getValue();

                    if (pvcList.isEmpty())
                        return StatusCount.of(id, objectNameMap.get(id), 0L, new LinkedHashMap<>(initialValueMap));

                    Map<String, Long> valueCounts = new LinkedHashMap<>(initialValueMap);
                    long totalCount = 0L;

                    for (PerValueCount pvc : pvcList) {
                        valueCounts.put(pvc.getValue(), pvc.getCount());
                        totalCount += pvc.getCount();
                    }

                    return StatusCount.of(id, objectNameMap.get(id), totalCount, valueCounts);
                })
                .sequential();
    }
}
