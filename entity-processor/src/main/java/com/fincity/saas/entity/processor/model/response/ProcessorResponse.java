package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.common.ProcessorStatus;
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
public class ProcessorResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 622206716081526076L;

    private String transId;
    private ProcessorStatus status;
    private EntitySeries entitySeries;
    private String details;

    private static ProcessorResponse of(
            String transId, ProcessorStatus status, EntitySeries entitySeries, String details) {
        return new ProcessorResponse()
                .setTransId(transId)
                .setStatus(status)
                .setDetails(details)
                .setEntitySeries(entitySeries);
    }

    private static ProcessorResponse of(
            String transId, ProcessorStatus status, EntitySeries entitySeries, String... details) {
        return new ProcessorResponse()
                .setTransId(transId)
                .setStatus(status)
                .setEntitySeries(entitySeries)
                .setDetails(status.getReasonPhrase(), details);
    }

    public static ProcessorResponse ofSuccess(String transId, EntitySeries series, String... details) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.OK), series, details);
    }

    public static ProcessorResponse ofCreated(String transId, EntitySeries series, String... details) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.CREATED), series, details);
    }

    public static ProcessorResponse ofBadRequest(String transId, EntitySeries series, String... details) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.BAD_REQUEST), series, details);
    }

    public ProcessorResponse setDetails(String detail, String... extraDetails) {
        this.details = Stream.concat(
                        Stream.of(detail),
                        extraDetails == null
                                ? Stream.empty()
                                : Arrays.stream(extraDetails).filter(s -> s != null && !s.isEmpty()))
                .collect(Collectors.joining(":"));
        return this;
    }
}
