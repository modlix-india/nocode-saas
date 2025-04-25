package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.base.ProcessorStatus;
import java.io.Serial;
import java.io.Serializable;
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
    private String details;

    private static ProcessorResponse of(String transId, ProcessorStatus status, String details) {
        return new ProcessorResponse().setTransId(transId).setStatus(status).setDetails(details);
    }

    private static ProcessorResponse of(String transId, ProcessorStatus status) {
        return new ProcessorResponse().setTransId(transId).setStatus(status).setDetails(status.getReasonPhrase());
    }

    public static ProcessorResponse ofSuccess(String transId, EntitySeries series) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.OK));
    }

    public static ProcessorResponse ofCreated(String transId, EntitySeries series) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.CREATED));
    }

    public static ProcessorResponse ofBadRequest(String transId, EntitySeries series) {
        return of(transId, ProcessorStatus.of(series, HttpStatus.BAD_REQUEST));
    }
}
