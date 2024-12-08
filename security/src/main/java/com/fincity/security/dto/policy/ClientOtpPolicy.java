package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fincity.saas.commons.util.CodeUtil;

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

	private boolean isNumeric;
	private boolean isAlphanumeric;
	private UShort length;
	private ULong expireInterval;

	@Override
	public String generate() {
		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
				.setLength(this.length.intValue())
				.setNumeric(this.isNumeric)
				.setUppercase(this.isAlphanumeric)
				.setLowercase(this.isAlphanumeric);

		return CodeUtil.generate(config);
	}
}
