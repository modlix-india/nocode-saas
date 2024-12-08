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
public class ClientPinPolicy extends AbstractPolicy {

	@Serial
	private static final long serialVersionUID = 6320470382858314209L;

	private UShort length;
	private ULong reLoginAfterInterval;
	private UShort expiryInDays;
	private UShort expiryWarnInDays;

	@Override
	public String generate() {
		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
				.setLength(this.length.intValue())
				.setNumeric(true)
				.setUppercase(false)
				.setLowercase(false)
				.setSpecialChars(false);

		return CodeUtil.generate(config);
	}
}
