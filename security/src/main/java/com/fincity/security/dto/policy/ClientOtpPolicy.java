package com.fincity.security.dto.policy;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private boolean isConstant = false;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String constantValue;
	private boolean isNumeric = true;
	private boolean isAlphanumeric = false;
	private Short length = 4;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private boolean resendSameOtp = false;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short noResendAttempts = 3;
	private Long expireInterval = 5L;

	@Override
	@JsonIgnore
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
