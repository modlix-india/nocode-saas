package com.fincity.security.dto;

import org.jooq.types.ULong;
import org.jooq.types.UShort;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ClientPasswordPolicy extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -6555166027839281210L;

	private ULong clientId;
	private boolean atleastOneUppercase;
	private boolean atleastoneLowercase;
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
