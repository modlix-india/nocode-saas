package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.base.ULongEager;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
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

    private ULongEager ownerId;
    private ULong assignedUserId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private ULongEager productId;
    private ULongEager stage;
    private ULongEager status;
    private String source;
    private String subSource;

    public static Ticket of(TicketRequest ticketRequest) {
        return new Ticket()
                .setDialCode(ticketRequest.getPhoneNumber().getCountryCode())
                .setPhoneNumber(ticketRequest.getPhoneNumber().getNumber())
                .setEmail(ticketRequest.getEmail().getAddress())
                .setSource(ticketRequest.getSource())
                .setSubSource(ticketRequest.getSubSource() != null ? ticketRequest.getSubSource() : null)
                .setName(ticketRequest.getName())
                .setDescription(ticketRequest.getDescription());
    }

    public ULong getOwnerId() {
        return ownerId.getId();
    }

    public Ticket setOwnerId(ULong ownerId) {
        this.ownerId = ULongEager.of(ownerId);
        return this;
    }

    public ULong getProductId() {
        return productId.getId();
    }

    public Ticket setProductId(ULong productId) {
        this.productId = ULongEager.of(productId);
        return this;
    }

    public ULong getStage() {
        return stage.getId();
    }

    public Ticket setStage(ULong stageId) {
        this.stage = ULongEager.of(stageId);
        return this;
    }

    public ULong getStatus() {
        return status.getId();
    }

    public Ticket setStatus(ULong statusId) {
        this.status = ULongEager.of(statusId);
        return this;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    public Ticket setSource(String source) {
        if (StringUtil.safeIsBlank(source)) return this;

        this.source = NameUtil.normalizeToUpper(source);
        return this;
    }

    public Ticket setSubSource(String subSource) {
        if (StringUtil.safeIsBlank(subSource)) return this;

        this.subSource = NameUtil.normalizeToUpper(subSource);
        return this;
    }
}
