package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fincity.saas.commons.util.CodeUtil;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ClientOtpPolicy extends AbstractPolicy {

	@Serial
	private static final long serialVersionUID = 5872464330396067248L;

	private SecurityClientOtpPolicyTargetType targetType = SecurityClientOtpPolicyTargetType.EMAIL;
	private boolean isConstant = false;
	private String constantValue;
	private boolean isNumeric = true;
	private boolean isAlphanumeric = false;
	private UShort length = UShort.valueOf(4);
	private boolean resendSameOtp = false;
	private UShort noResendAttempts = UShort.valueOf(3);
	private ULong expireInterval = ULong.valueOf(5);

	@Override
	public String generate() {

		if (this.isConstant) {
			return this.constantValue;
		}

		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
				.setLength(this.length.intValue())
				.setNumeric(this.isNumeric)
				.setUppercase(this.isAlphanumeric)
				.setLowercase(this.isAlphanumeric);

		return CodeUtil.generate(config);
	}
}
