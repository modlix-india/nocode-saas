package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketRequest extends BaseRequest<TicketRequest> implements INoteRequest {

    @Serial
    private static final long serialVersionUID = 3948634318723751023L;

    private Identity productId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String source;
    private String subSource;
    private Boolean dnc;
    private NoteRequest noteRequest;
    private String comment;
    private Identity campaignId;

    public boolean hasIdentifyInfo() {
        return this.getPhoneNumber() != null || this.getEmail() != null;
    }

    public boolean hasSourceInfo() {
        return this.source != null && !this.source.isEmpty();
    }
}
