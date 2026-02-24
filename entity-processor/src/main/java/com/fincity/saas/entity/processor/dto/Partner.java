package com.fincity.saas.entity.processor.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.ClientFieldResolver;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
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
@IgnoreGeneration
public class Partner extends BaseUpdatableDto<Partner> {

    @Serial
    private static final long serialVersionUID = 3748035430910724547L;

    private ULong clientId;

    /**
     * @deprecated Manager linkage has moved to security_client_manager.
     *             This field is kept only for backward compatibility and will be removed later.
     */
    @Deprecated(forRemoval = true)
    @JsonIgnore
    private ULong managerId;

    private PartnerVerificationStatus partnerVerificationStatus = PartnerVerificationStatus.INVITATION_SENT;
    private Boolean dnc = Boolean.FALSE;

    public Partner() {
        super();
        this.relationsResolverMap.put(ClientFieldResolver.class, Fields.clientId);
    }

    public Partner(Partner partner) {
        super(partner);
        this.clientId = partner.clientId;
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
}
