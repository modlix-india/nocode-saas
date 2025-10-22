package com.fincity.saas.entity.processor.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormTicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import java.io.Serial;
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
public class Ticket extends BaseProcessorDto<Ticket> {

    @Serial
    private static final long serialVersionUID = 1639822311147907381L;

    private ULong ownerId;
    private ULong assignedUserId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private ULong productId;
    private ULong stage;
    private ULong status;
    private String source;
    private String subSource;
    private ULong campaignId;
    private Boolean dnc = Boolean.FALSE;

    public Ticket() {
        super();
        this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
        this.relationsMap.put(Fields.stage, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.status, EntitySeries.STAGE.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.assignedUserId);
        this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
    }

    public Ticket(Ticket ticket) {
        super(ticket);
        this.ownerId = ticket.ownerId;
        this.assignedUserId = ticket.assignedUserId;
        this.dialCode = ticket.dialCode;
        this.phoneNumber = ticket.phoneNumber;
        this.email = ticket.email;
        this.productId = ticket.productId;
        this.stage = ticket.stage;
        this.status = ticket.status;
        this.source = ticket.source;
        this.subSource = ticket.subSource;
        this.campaignId = ticket.campaignId;
        this.dnc = ticket.dnc;
    }

    public static Ticket of(TicketRequest ticketRequest) {
        return new Ticket()
                .setDialCode(
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getCountryCode()
                                : null)
                .setPhoneNumber(
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getNumber()
                                : null)
                .setEmail(
                        ticketRequest.getEmail() != null
                                ? ticketRequest.getEmail().getAddress()
                                : null)
                .setSource(ticketRequest.getSource())
                .setSubSource(ticketRequest.getSubSource() != null ? ticketRequest.getSubSource() : null)
                .setName(ticketRequest.getName())
                .setDescription(ticketRequest.getDescription());
    }

    public static Ticket of(CampaignTicketRequest campaignTicketRequest) {
        return new Ticket()
                .setDialCode(
                        campaignTicketRequest.getLeadDetails().getPhone() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getPhone()
                                        .getCountryCode()
                                : null)
                .setPhoneNumber(
                        campaignTicketRequest.getLeadDetails().getPhone() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getPhone()
                                        .getNumber()
                                : null)
                .setEmail(
                        campaignTicketRequest.getLeadDetails().getEmail() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getEmail()
                                        .getAddress()
                                : null)
                .setSource(campaignTicketRequest.getLeadDetails().getSource())
                .setSubSource(
                        campaignTicketRequest.getLeadDetails().getSubSource() != null
                                ? campaignTicketRequest.getLeadDetails().getSubSource()
                                : null)
                .setName(
                        campaignTicketRequest.getLeadDetails().getFullName() != null
                                ? campaignTicketRequest.getLeadDetails().getFullName()
                                : campaignTicketRequest.getLeadDetails().getFirstName() + " "
                                        + campaignTicketRequest.getLeadDetails().getLastName());
    }

    public static Ticket of(WalkInFormTicketRequest walkInFormTicketRequest) {
        return new Ticket()
                .setDialCode(
                        walkInFormTicketRequest.getPhoneNumber() != null
                                ? walkInFormTicketRequest.getPhoneNumber().getCountryCode()
                                : null)
                .setPhoneNumber(
                        walkInFormTicketRequest.getPhoneNumber() != null
                                ? walkInFormTicketRequest.getPhoneNumber().getNumber()
                                : null)
                .setEmail(
                        walkInFormTicketRequest.getEmail() != null
                                ? walkInFormTicketRequest.getEmail().getAddress()
                                : null)
                .setSource(walkInFormTicketRequest.getSource())
                .setSubSource(
                        walkInFormTicketRequest.getSubSource() != null ? walkInFormTicketRequest.getSubSource() : null)
                .setName(walkInFormTicketRequest.getName())
                .setDescription(walkInFormTicketRequest.getDescription());
    }

    @Override
    @JsonIgnore
    public ULong getAccessUser() {
        return this.getAssignedUserId();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    public Ticket setSource(String source) {
        if (StringUtil.safeIsBlank(source)) return this;

        this.source = NameUtil.normalize(source);
        return this;
    }

    public Ticket setSubSource(String subSource) {
        if (StringUtil.safeIsBlank(subSource)) return this;

        this.subSource = NameUtil.normalize(subSource);
        return this;
    }
}
