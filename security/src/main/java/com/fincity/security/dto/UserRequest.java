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
}
