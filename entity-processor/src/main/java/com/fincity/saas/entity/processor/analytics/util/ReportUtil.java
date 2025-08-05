package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@UtilityClass
public class ReportUtil {

    public static <T> Flux<StatusCount> toStatusCounts(
            Flux<PerValueCount> perValueCountFlux,
            Mono<Map<ULong, String>> objectNameMapMono,
            Flux<T> requiredValueFlux,
            Function<T, String> getNameFunction,
            Boolean includeZero) {

        Mono<Map<String, Long>> initialValueMapMono = requiredValueFlux.collect(
                Collectors.toMap(getNameFunction, v -> 0L, (existing, replacement) -> existing, LinkedHashMap::new));

        return Mono.zip(initialValueMapMono, objectNameMapMono)
                .flatMapMany(
                        tuple -> processToStatusCounts(perValueCountFlux, tuple.getT2(), tuple.getT1(), includeZero));
    }

    public static <T> Flux<StatusCount> toStatusCounts(
            Flux<PerValueCount> perValueCountFlux,
            Map<ULong, String> objectNameMap,
            List<T> requiredValueList,
            Function<T, String> getNameFunction,
            Boolean includeZero) {

        return toStatusCounts(
                perValueCountFlux,
                Mono.just(objectNameMap),
                Flux.fromIterable(requiredValueList),
                getNameFunction,
                includeZero);
    }

    public static Flux<StatusCount> toStatusCounts(
            Flux<PerValueCount> perValueCountFlux, Mono<Map<ULong, String>> objectNameMapMono) {

        return objectNameMapMono.flatMapMany(objectNameMap -> perValueCountFlux.map(pvc -> StatusCount.of(
                pvc.getId(), objectNameMap.get(pvc.getId()), pvc.getCount(), Map.of(pvc.getValue(), pvc.getCount()))));
    }

    public static Flux<StatusCount> toStatusCounts(
            Flux<PerValueCount> perValueCountFlux, Map<ULong, String> objectNameMap) {
        return toStatusCounts(perValueCountFlux, Mono.just(objectNameMap));
    }

    private static Flux<StatusCount> processToStatusCounts(
            Flux<PerValueCount> perValueCountFlux,
            Map<ULong, String> objectNameMap,
            Map<String, Long> initialValueMap,
            boolean includeZero) {

        return perValueCountFlux
                .collect(Collectors.groupingBy(PerValueCount::getId, LinkedHashMap::new, Collectors.toList()))
                .flatMapMany(grouped -> includeZero
                        ? addZeroCountsAndConvert(grouped, objectNameMap, initialValueMap)
                        : convertGroupsToStatusCounts(grouped, objectNameMap, initialValueMap));
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
                    List<PerValueCount> perValueCountList = entry.getValue();

                    if (perValueCountList.isEmpty())
                        return StatusCount.of(id, objectNameMap.get(id), 0L, new LinkedHashMap<>(initialValueMap));

                    Map<String, Long> valueCounts = new LinkedHashMap<>(initialValueMap);
                    long totalCount = 0L;

                    for (PerValueCount pvc : perValueCountList) {
                        valueCounts.put(pvc.getValue(), pvc.getCount());
                        totalCount += pvc.getCount();
                    }

                    return StatusCount.of(id, objectNameMap.get(id), totalCount, valueCounts);
                })
                .sequential();
    }
}
