package com.fincity.security.dto;

import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
@JsonIgnoreType
public class Otp extends AbstractDTO<ULong, ULong> {

	private ULong appId;
	private ULong userId;
	private String emailId = null;
	private String phoneNumber = null;
	private String purpose;
	private SecurityOtpTargetType targetType;
	private String uniqueCode;
	private LocalDateTime expiresAt;
	private String ipAddress;

	@Serial
	private void writeObject(ObjectOutputStream out) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	@Serial
	private void readObject(ObjectInputStream in) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	public boolean isExpired() {
		return this.expiresAt.isBefore(LocalDateTime.now());
	}
}
