package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.ClientFieldResolver;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.UserFieldResolver;
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
public class Partner extends BaseUpdatableDto<Partner> {

    @Serial
    private static final long serialVersionUID = 3748035430910724547L;

    private ULong clientId;
    private ULong managerId;
    private PartnerVerificationStatus partnerVerificationStatus = PartnerVerificationStatus.INVITATION_SENT;
    private Boolean dnc = Boolean.FALSE;

    public Partner() {
        super();
        this.relationsResolverMap.put(ClientFieldResolver.class, Partner.Fields.clientId);
        this.relationsResolverMap.put(UserFieldResolver.class, Partner.Fields.managerId);
    }

    public Partner(Partner partner) {
        super(partner);
        this.clientId = partner.clientId;
        this.managerId = partner.managerId;
        this.partnerVerificationStatus = partner.partnerVerificationStatus;
        this.dnc = partner.dnc;
    }

    public static Partner of(PartnerRequest partnerRequest) {
        return new Partner()
                .setName(partnerRequest.getName())
                .setDescription(partnerRequest.getDescription())
                .setClientId(partnerRequest.getClientId());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PARTNER;
    }
}
