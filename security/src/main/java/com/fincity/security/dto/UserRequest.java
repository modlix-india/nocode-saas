package com.fincity.security.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRequest extends AbstractUpdatableDTO<ULong, ULong> {
    private String requestId;
    private ULong clientId;
    private ULong userId;
    private ULong appId;

    private SecurityUserRequestStatus status;

    // Display names resolved on read, not columns. A request row is a status and
    // three foreign keys; without these the pane has to fan out one query per id,
    // which is what orgRequests used to do.
    // Filled by UserRequestService.fillDetails; left null when the id no longer
    // resolves. decidedByName is who approved or rejected it - the UPDATED_BY of
    // the row, which is null while the request is still pending.
    private String requesterName;
    private String requesterUserName;
    private String requesterEmail;
    private String clientName;
    private String appName;
    private String appCode;
    private String decidedByName;
}
