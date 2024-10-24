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
import com.fincity.security.util.HashUtil;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
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
	private String purpose;
	private SecurityOtpTargetType targetType;

	@Setter(AccessLevel.NONE)
	private String uniqueCode;

	private LocalDateTime expiresAt;
	private int attempts = 0;
	private String ipAddress;

	@Serial
	private void writeObject(ObjectOutputStream out) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	@Serial
	private void readObject(ObjectInputStream in) throws NotSerializableException {
		throw new NotSerializableException("OTP class is not Serializable");
	}

	public void increaseAttempts() {
		this.attempts++;
	}

	public Otp setUniqueCode(String uniqueCode) {
		this.uniqueCode = HashUtil.hash(uniqueCode);
		return this;
	}

	public boolean match(String uniqueCode) {
		return this.uniqueCode.equals(HashUtil.hash(uniqueCode));
	}

}
