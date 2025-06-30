package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OwnerRequest extends BaseRequest<OwnerRequest> {

    @Serial
    private static final long serialVersionUID = 8432447203359141912L;

    private PhoneNumber phoneNumber;
    private Email email;
    private String source;
    private String subSource;

    public static OwnerRequest of(TicketRequest ticketRequest) {
        return new OwnerRequest()
                .setName(ticketRequest.getName())
                .setDescription(ticketRequest.getDescription())
                .setPhoneNumber(ticketRequest.getPhoneNumber())
                .setEmail(ticketRequest.getEmail())
                .setSource(ticketRequest.getSource())
                .setSubSource(ticketRequest.getSubSource());
    }
}
