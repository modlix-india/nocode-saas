package com.fincity.saas.message.model.response;

import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.model.common.MessageStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class MessageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 622206716081526076L;

    private String transId;
    private MessageStatus status;
    private MessageSeries messageSeries;
    private String details;

    private static MessageResponse of(
            String transId, MessageStatus status, MessageSeries messageSeries, String details) {
        return new MessageResponse()
                .setTransId(transId)
                .setStatus(status)
                .setDetails(details)
                .setMessageSeries(messageSeries);
    }

    private static MessageResponse of(
            String transId, MessageStatus status, MessageSeries messageSeries, String... details) {
        return new MessageResponse()
                .setTransId(transId)
                .setStatus(status)
                .setMessageSeries(messageSeries)
                .setDetails(status.getReasonPhrase(), details);
    }

    public static MessageResponse ofSuccess(String transId, MessageSeries series, String... details) {
        return of(transId, MessageStatus.of(series, HttpStatus.OK), series, details);
    }

    public static MessageResponse ofCreated(String transId, MessageSeries series, String... details) {
        return of(transId, MessageStatus.of(series, HttpStatus.CREATED), series, details);
    }

    public static MessageResponse ofBadRequest(String transId, MessageSeries series, String... details) {
        return of(transId, MessageStatus.of(series, HttpStatus.BAD_REQUEST), series, details);
    }

    public MessageResponse setDetails(String detail, String... extraDetails) {
        this.details = Stream.concat(
                        Stream.of(detail),
                        extraDetails == null
                                ? Stream.empty()
                                : Arrays.stream(extraDetails).filter(s -> s != null && !s.isEmpty()))
                .collect(Collectors.joining(":"));
        return this;
    }
}
