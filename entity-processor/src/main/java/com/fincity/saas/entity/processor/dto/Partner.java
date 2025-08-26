package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;

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
public class Partner extends BaseUpdatableDto<Partner> {

	@Serial
	private static final long serialVersionUID = 3748035430910724547L;

	private ULong clientId;
	private ULong userId;
	private ULong managerId;
	private PartnerVerificationStatus partnerVerificationStatus;

	public Partner() {
		super();
	}

	public Partner(Partner partner) {
		super(partner);
		this.clientId = partner.clientId;
		this.userId = partner.userId;
		this.managerId = partner.managerId;
		this.partnerVerificationStatus = partner.partnerVerificationStatus;
	}

	@Override
	public EntitySeries getEntitySeries() {
		return null;
	}

}
