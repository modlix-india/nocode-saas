package com.fincity.saas.message.dto.call;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Call extends BaseUpdatableDto<Call> {

    @Serial
    private static final long serialVersionUID = 6948416006208004030L;

    private ULong userId;
    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;
    private String callerId;
    private String connectionName;
    private String callProvider;
    private Boolean isOutbound;
    private CallStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private String recordingUrl;
    private ULong exotelCallId;
    private Map<String, Object> metadata;
}
