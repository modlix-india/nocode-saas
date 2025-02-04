package com.fincity.security.dto.policy;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

	private Short length = 4;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long reLoginAfterInterval = 120L;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short expiryInDays = 30;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short expiryWarnInDays = 25;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short pinHistoryCount = 3;

	@Override
	@JsonIgnore
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
