package com.fincity.security.model.otp;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.enums.otp.OtpPurpose;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 4043506794111804445L;

	private String emailId;
	private String phoneNumber;
	private OtpPurpose purpose;
	private String otp;

	public boolean isNotValid() {
		return this.isOtpNotValid() || this.isIdentifiersNotValid();
	}

	public boolean isOtpNotValid() {
		return StringUtil.safeIsBlank(otp) && purpose == null;
	}

	public boolean isIdentifiersNotValid() {
		return StringUtil.safeIsBlank(emailId) && StringUtil.safeIsBlank(phoneNumber);
	}

}
