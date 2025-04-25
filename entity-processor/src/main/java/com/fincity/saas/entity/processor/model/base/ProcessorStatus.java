package com.fincity.saas.entity.processor.model.base;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
public class ProcessorStatus implements Serializable {

    private static final Map<EntitySeries, Map<HttpStatus, ProcessorStatus>> STATUS_MAP = new ConcurrentHashMap<>();

    private final int value;
    private final EntitySeries series;
    private final HttpStatus httpStatus;
    private final String reasonPhrase;

    private ProcessorStatus(EntitySeries series, HttpStatus httpStatus, String reasonPhrase) {
        this.series = series;
        this.httpStatus = httpStatus;
        this.value = (series.getValue() * 1000) + httpStatus.value();
        this.reasonPhrase = reasonPhrase;
    }

    public static ProcessorStatus of(EntitySeries series, HttpStatus httpStatus) {
        return STATUS_MAP
                .computeIfAbsent(series, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(
                        httpStatus,
                        status -> new ProcessorStatus(series, status, series.name() + " " + status.getReasonPhrase()));
    }

    @Override
    public String toString() {
        return this.value + " " + reasonPhrase;
    }
}
