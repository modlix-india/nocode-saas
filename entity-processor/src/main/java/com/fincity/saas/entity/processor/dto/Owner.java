package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.OwnerRequest;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.fincity.saas.entity.processor.util.PhoneUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Owner extends BaseProcessorDto<Owner> {

    @Serial
    private static final long serialVersionUID = 3722918782975754023L;

    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private String source;
    private String subSource;

    public static Owner of(OwnerRequest ownerRequest) {
        return new Owner()
                .setDialCode(ownerRequest.getPhoneNumber().getCountryCode())
                .setPhoneNumber(ownerRequest.getPhoneNumber().getNumber())
                .setEmail(ownerRequest.getEmail().getAddress())
                .setSource(ownerRequest.getSource())
                .setSubSource(ownerRequest.getSubSource() != null ? ownerRequest.getSubSource() : null)
                .setName(ownerRequest.getName())
                .setDescription(ownerRequest.getDescription());
    }

    public static Owner of(Ticket ticket) {
        return (Owner) new Owner()
                .setDialCode(ticket.getDialCode())
                .setPhoneNumber(ticket.getPhoneNumber())
                .setEmail(ticket.getEmail())
                .setSource(ticket.getSource())
                .setSubSource(ticket.getSubSource() != null ? ticket.getSubSource() : null)
                .setName(ticket.getName())
                .setDescription(ticket.getDescription())
                .setAppCode(ticket.getAppCode())
                .setClientCode(ticket.getClientCode());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.OWNER;
    }

    public Owner setSource(String source) {
        if (StringUtil.safeIsBlank(source)) return this;

        this.source = NameUtil.normalize(source);
        return this;
    }

    public Owner setSubSource(String subSource) {
        if (StringUtil.safeIsBlank(subSource)) return this;

        this.subSource = NameUtil.normalize(subSource);
        return this;
    }
}
