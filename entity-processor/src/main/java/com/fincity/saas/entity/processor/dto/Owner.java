package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.OwnerRequest;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.Map;
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

    public Owner() {
        super();
    }

    public Owner(Owner owner) {
        super(owner);
        this.dialCode = owner.dialCode;
        this.phoneNumber = owner.phoneNumber;
        this.email = owner.email;
        this.source = owner.source;
        this.subSource = owner.subSource;
    }

    public static Owner of(OwnerRequest ownerRequest) {
        return new Owner()
                .setDialCode(
                        ownerRequest.getPhoneNumber() != null
                                ? ownerRequest.getPhoneNumber().getCountryCode()
                                : null)
                .setPhoneNumber(
                        ownerRequest.getPhoneNumber() != null
                                ? ownerRequest.getPhoneNumber().getNumber()
                                : null)
                .setEmail(
                        ownerRequest.getEmail() != null
                                ? ownerRequest.getEmail().getAddress()
                                : null)
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

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(
                Fields.dialCode,
                DbSchema.ofDialCode(Fields.dialCode)
                        .setDefaultValue(new JsonPrimitive(PhoneUtil.getDefaultCallingCode())));
        props.put(Fields.phoneNumber, DbSchema.ofPhoneNumber(Fields.phoneNumber));
        props.put(Fields.email, DbSchema.ofEmail(Fields.email));
        props.put(Fields.source, DbSchema.ofChar(Ticket.Fields.source, 32));
        props.put(Fields.subSource, DbSchema.ofCharNull(Ticket.Fields.subSource, 32));

        schema.setProperties(props);
    }
}
