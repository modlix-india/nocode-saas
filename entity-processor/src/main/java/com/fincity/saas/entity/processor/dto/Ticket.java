package com.fincity.saas.entity.processor.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.relations.resolvers.UserFieldResolver;
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

    public Ticket() {
        super();
        this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
        this.relationsMap.put(Fields.stage, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.status, EntitySeries.STAGE.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.assignedUserId);
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
