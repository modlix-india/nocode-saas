package com.fincity.security.dto;

import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ClientPasswordPolicy extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -6555166027839281210L;

	private ULong clientId;
	private ULong appId;
	private boolean atleastOneUppercase;
	private boolean atleastOneLowercase;
	private boolean atleastOneDigit;
	private boolean atleastOneSpecialChar;
	private boolean spacesAllowed;
	private String regex;
	private UShort percentageName;
	private UShort passExpiryInDays;
	private UShort passExpiryWarnInDays;
	private UShort passMinLength;
	private UShort passMaxLength;
	private UShort noFailedAttempts;
	private UShort passHistoryCount;
}
