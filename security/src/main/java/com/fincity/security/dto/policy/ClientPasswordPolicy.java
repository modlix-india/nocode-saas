package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.UShort;

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
	private UShort percentageName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort passExpiryInDays;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort passExpiryWarnInDays;
	private UShort passMinLength;
	private UShort passMaxLength;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort passHistoryCount;

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
