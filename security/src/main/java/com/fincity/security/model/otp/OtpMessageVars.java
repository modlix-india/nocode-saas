package com.fincity.security.model.otp;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.security.enums.otp.OtpPurpose;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OtpMessageVars implements Serializable {

	@Serial
	private static final long serialVersionUID = 8389793285676512877L;

	private String otpCode;
	private OtpPurpose otpPurpose;
	private Long expireInterval = 5L;

}
