package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.oserver.message.enums.call.CallStatus;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CallLogRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Identity ticketId;
    private Boolean isOutbound; // true for outgoing call, false for incoming call
    private CallStatus callStatus;
    private LocalDateTime callDate;
    private Long callDuration; // Duration in seconds
    private String comment;
    private String customer;
}
