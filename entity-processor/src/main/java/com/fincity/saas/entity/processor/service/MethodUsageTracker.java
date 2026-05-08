package com.fincity.saas.entity.processor.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MethodUsageTracker {

    private final ConcurrentHashMap<String, LongAdder> callCounts = new ConcurrentHashMap<>();
    private volatile LocalDateTime trackingSince = LocalDateTime.now();

    public void record(String methodSignature) {
        callCounts.computeIfAbsent(methodSignature, k -> new LongAdder()).increment();
    }

    public Map<String, Long> getCounts() {
        return callCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, LongAdder>>comparingLong(e -> e.getValue().sum())
                        .reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum(), (a, b) -> a, LinkedHashMap::new));
    }

    public Map<String, Object> getReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("trackingSince", trackingSince.toString());
        report.put("totalMethods", callCounts.size());
        report.put("totalCalls", callCounts.values().stream().mapToLong(LongAdder::sum).sum());
        report.put("methods", getCounts());
        return report;
    }

    public void reset() {
        callCounts.clear();
        trackingSince = LocalDateTime.now();
    }
}
