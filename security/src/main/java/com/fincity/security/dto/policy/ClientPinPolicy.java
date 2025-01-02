package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.ULong;
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
public class ClientPinPolicy extends AbstractPolicy {

	@Serial
	private static final long serialVersionUID = 6320470382858314209L;

	private UShort length = UShort.valueOf(4);

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong reLoginAfterInterval = ULong.valueOf(120);

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort expiryInDays = UShort.valueOf(30);

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort expiryWarnInDays = UShort.valueOf(25);

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private UShort pinHistoryCount = UShort.valueOf(3);

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
