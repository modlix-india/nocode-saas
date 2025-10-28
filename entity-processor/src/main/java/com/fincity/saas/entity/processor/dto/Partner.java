package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.relations.resolvers.field.ClientFieldResolver;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.Map;
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
public class Partner extends BaseUpdatableDto<Partner> {

    @Serial
    private static final long serialVersionUID = 3748035430910724547L;

    private ULong clientId;
    private ULong managerId;
    private PartnerVerificationStatus partnerVerificationStatus = PartnerVerificationStatus.INVITATION_SENT;
    private boolean dnc;

    public Partner() {
        super();
        this.relationsResolverMap.put(ClientFieldResolver.class, Fields.clientId);
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.managerId);
    }

    public Partner(Partner partner) {
        super(partner);
        this.clientId = partner.clientId;
        this.managerId = partner.managerId;
        this.partnerVerificationStatus = partner.partnerVerificationStatus;
        this.dnc = partner.dnc;
    }

    public static Partner of(PartnerRequest partnerRequest) {
        return new Partner().setClientId(partnerRequest.getClientId()).setDnc(partnerRequest.getDnc());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PARTNER;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(Fields.clientId, Schema.ofLong(Fields.clientId).setMinimum(1));
        props.put(Fields.managerId, Schema.ofLong(Fields.managerId).setMinimum(1));
        props.put(
                Fields.partnerVerificationStatus,
                Schema.ofString(Fields.partnerVerificationStatus)
                        .setEnums(EnumSchemaUtil.getSchemaEnums(PartnerVerificationStatus.class))
                        .setDefaultValue(new JsonPrimitive(PartnerVerificationStatus.INVITATION_SENT.name())));
        props.put(Fields.dnc, Schema.ofBoolean(Fields.dnc).setDefaultValue(new JsonPrimitive(false)));

        schema.setProperties(props);
        return schema;
    }
}
