package com.fincity.saas.entity.processor.model.request.ticket;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketPartnerRequest extends BaseRequest<TicketRequest> {

    private ULong clientId;
    private ULong assignedUserId;
    private Identity productId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String source = "Business Partner";
    private String subSource = "Developer CRM";
    private String comment;
    private Identity stageId;
    private Identity statusId;
    private LocalDateTime createdDate;
    private Map<String, Object> activityJson;
}
