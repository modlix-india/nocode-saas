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
public class OtpGenerationRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 3482978077027364329L;

	private String emailId;
	private String phoneNumber;
	private boolean isResend = Boolean.FALSE;
	private OtpPurpose purpose;
}
