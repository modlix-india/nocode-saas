package com.fincity.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.security.enums.otp.OtpPurpose;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OtpGenerationRequest {

	private String emailId;
	private String phoneNumber;
	private boolean isResend = Boolean.FALSE;
	private OtpPurpose purpose;

	@JsonIgnore
	private Short verifyLegsCounts = 0;
}
