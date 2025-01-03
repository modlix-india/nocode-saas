package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetSocketAddress;

import org.jooq.types.ULong;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;

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
	private static final long serialVersionUID = 7835243941994699978L;

	private ULong clientId;
	private String clientCode;
	private ULong appId;
	private String appCode;
	private String appName;
	private ULong userId;
	private boolean withUser;
	private String emailId;
	private String phoneNumber;
	private SecurityOtpTargetType targetType;
	private boolean isResend = Boolean.FALSE;
	private String purpose;
	private String ipAddress;

	public OtpGenerationRequest setClientOption(Client client) {
		if (client != null) {
			this.clientId = client.getId();
			this.clientCode = client.getCode();
		}
		return this;
	}

	public OtpGenerationRequest setAppOption(App app) {
		if (app != null) {
			this.appId = app.getId();
			this.appCode = app.getAppCode();
			this.appName = app.getAppName();
		}
		return this;
	}

	public OtpGenerationRequest setWithUserOption(User user) {
		if (user != null) {
			this.userId = user.getId();
			this.withUser = true;
			this.setEmailId(user.getEmailId());
			this.setPhoneNumber(user.getPhoneNumber());
		}
		return this;
	}

	public OtpGenerationRequest setWithoutUserOption(String emailId, String phoneNumber) {
		this.userId = null;
		this.withUser = false;
		this.setEmailId(emailId);
		this.setPhoneNumber(phoneNumber);
		return this;
	}

	public OtpGenerationRequest setEmailId(String emailId) {
		this.emailId = StringUtil.safeIsBlank(emailId) ? null : emailId;
		return this;
	}

	public OtpGenerationRequest setPhoneNumber(String phoneNumber) {
		this.phoneNumber = StringUtil.safeIsBlank(phoneNumber) ? null : phoneNumber;
		return this;
	}

	public OtpGenerationRequest setTargetOption(SecurityOtpTargetType targetType) {

		this.targetType = targetType;

		if (this.targetType == SecurityOtpTargetType.EMAIL) {
			this.phoneNumber = null;
		} else if (this.targetType == SecurityOtpTargetType.PHONE) {
			this.emailId = null;
		}
		return this;
	}

	public OtpGenerationRequest setPurpose(OtpPurpose otpPurpose) {
		this.purpose = otpPurpose == null ? null : otpPurpose.name();
		return this;
	}

	public OtpGenerationRequest setIpAddress(InetSocketAddress ipAddress) {
		this.ipAddress = ipAddress == null ? null : ipAddress.getAddress().getHostAddress();
		return this;
	}
}
