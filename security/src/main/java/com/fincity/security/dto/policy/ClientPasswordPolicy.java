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
public class ClientPasswordPolicy extends AbstractPolicy {

	@Serial
	private static final long serialVersionUID = -6555166027839281210L;

	private boolean atleastOneUppercase;
	private boolean atleastOneLowercase;
	private boolean atleastOneDigit;
	private boolean atleastOneSpecialChar;
	private boolean spacesAllowed;
	private String regex;
	private Short percentageName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short passExpiryInDays;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short passExpiryWarnInDays;
	private Short passMinLength;
	private Short passMaxLength;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Short passHistoryCount;

	@Override
	@JsonIgnore
	public String generate() {
		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
				.setLength(this.passMinLength.intValue())
				.setUppercase(this.atleastOneUppercase)
				.setLowercase(this.atleastOneLowercase)
				.setNumeric(this.atleastOneDigit)
				.setSpecialChars(this.atleastOneSpecialChar);

		return CodeUtil.generate(config);
	}
}
