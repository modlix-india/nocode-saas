package com.fincity.security.dto;

import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.util.HashUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
@JsonIgnoreType
public class Otp extends AbstractUpdatableDTO<ULong, ULong> {

	private ULong appId;
	private ULong userId;
	private String purpose;
	private SecurityOtpTargetType targetType;
	private String uniqueCode;
	private LocalDateTime resendAt;
	private LocalDateTime expiresAt;
	private int resends = 0;
	private String ipAddress;

	@Serial
	private void writeObject(ObjectOutputStream out) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	@Serial
	private void readObject(ObjectInputStream in) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	public Otp increaseResend() {
		this.resends++;
		return this;
	}

	public boolean isExpired() {
		return this.expiresAt.isBefore(LocalDateTime.now());
	}

	public boolean match(String uniqueCode) {
		return HashUtil.equal(this.uniqueCode, uniqueCode);
	}

}
