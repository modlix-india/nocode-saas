package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import java.io.Serial;
import java.math.BigInteger;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

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
    private NoteRequest noteRequest;
    private String comment;
    private Identity campaignId;

    public static TicketRequest of(CampaignTicketRequest campaignTicketRequest, ULong productId, ULong campaignId) {

        TicketRequest ticketRequest = new TicketRequest()
                .setProductId(new Identity().setId(productId.toBigInteger()))
                .setPhoneNumber(new PhoneNumber()
                        .setNumber(campaignTicketRequest.getLeadDetails().getPhone()));

        if (campaignTicketRequest.getLeadDetails().getFullName() != null) {
            ticketRequest.setName(campaignTicketRequest.getLeadDetails().getFullName());
        } else if (campaignTicketRequest.getLeadDetails().getFirstName() != null) {
            if (campaignTicketRequest.getLeadDetails().getLastName() != null) {
                ticketRequest.setName(campaignTicketRequest.getLeadDetails().getFirstName() + " "
                        + campaignTicketRequest.getLeadDetails().getLastName());
            } else {
                ticketRequest.setName(campaignTicketRequest.getLeadDetails().getFirstName());
            }
        } else {
            ticketRequest.setName("New Customer");
        }

        if (campaignId != null) ticketRequest.setCampaignId(new Identity().setId(campaignId.toBigInteger()));

        if (campaignTicketRequest.getLeadDetails().getSource() != null)
            ticketRequest.setSource(campaignTicketRequest.getLeadDetails().getSource());

        if (campaignTicketRequest.getLeadDetails().getEmail() != null)
            ticketRequest.setEmail(new Email()
                    .setAddress(campaignTicketRequest.getLeadDetails().getEmail()));

        if (campaignTicketRequest.getLeadDetails().getSubSource() != null)
            ticketRequest.setSubSource(campaignTicketRequest.getLeadDetails().getSubSource());

        return ticketRequest;
    }

    public boolean hasIdentifyInfo() {
        return this.getPhoneNumber() != null || this.getEmail() != null;
    }

    public boolean hasSourceInfo() {
        return this.source != null && !this.source.isEmpty();
    }
}
