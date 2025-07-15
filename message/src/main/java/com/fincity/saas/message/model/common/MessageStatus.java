package com.fincity.saas.message.model.common;

import com.fincity.saas.message.enums.MessageSeries;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
public class MessageStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 6415447399628261212L;

    private static final Map<MessageSeries, Map<HttpStatus, MessageStatus>> STATUS_MAP = new ConcurrentHashMap<>();

    private final int value;
    private final MessageSeries series;
    private final HttpStatus httpStatus;
    private final String reasonPhrase;

    private MessageStatus(MessageSeries series, HttpStatus httpStatus, String reasonPhrase) {
        this.series = series;
        this.httpStatus = httpStatus;
        this.value = (series.getValue() * 1000) + httpStatus.value();
        this.reasonPhrase = reasonPhrase;
    }

    public static MessageStatus of(MessageSeries series, HttpStatus httpStatus) {
        return STATUS_MAP
                .computeIfAbsent(series, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(
                        httpStatus,
                        status -> new MessageStatus(series, status, series.name() + " " + status.getReasonPhrase()));
    }

    @Override
    public String toString() {
        return this.value + " " + reasonPhrase;
    }
}
