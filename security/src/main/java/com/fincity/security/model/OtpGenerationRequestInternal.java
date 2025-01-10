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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OtpGenerationRequestInternal extends OtpGenerationRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 7835243941994699978L;

	private ULong clientId;
	private String clientCode;
	private ULong appId;
	private String appCode;
	private String appName;
	private ULong userId;
	private boolean withUser;
	private String ipAddress;

	public OtpGenerationRequestInternal setClientOption(Client client) {
		if (client != null) {
			this.clientId = client.getId();
			this.clientCode = client.getCode();
		}
		return this;
	}

	public OtpGenerationRequestInternal setAppOption(App app) {
		if (app != null) {
			this.appId = app.getId();
			this.appCode = app.getAppCode();
			this.appName = app.getAppName();
		}
		return this;
	}

	public OtpGenerationRequestInternal setWithUserOption(User user) {
		if (user != null) {
			this.userId = user.getId();
			this.withUser = true;
			this.setEmailId(user.getEmailId());
			this.setPhoneNumber(user.getPhoneNumber());
		}
		return this;
	}

	public OtpGenerationRequestInternal setWithoutUserOption(String emailId, String phoneNumber) {
		this.userId = null;
		this.withUser = false;
		this.setEmailId(emailId);
		this.setPhoneNumber(phoneNumber);
		return this;
	}

	public OtpGenerationRequestInternal setIpAddress(InetSocketAddress ipAddress) {
		this.ipAddress = ipAddress == null ? null : ipAddress.getAddress().getHostAddress();
		return this;
	}

	@Override
	public OtpGenerationRequestInternal setEmailId(String emailId) {
		super.setEmailId(StringUtil.safeIsBlank(emailId) ? null : emailId);
		return this;
	}

	@Override
	public OtpGenerationRequestInternal setPhoneNumber(String phoneNumber) {
		super.setPhoneNumber(StringUtil.safeIsBlank(phoneNumber) ? null : phoneNumber);
		return this;
	}

	@Override
	public OtpGenerationRequestInternal setResend(boolean resend) {
		super.setResend(resend);
		return this;
	}

	@Override
	public OtpGenerationRequestInternal setPurpose(OtpPurpose otpPurpose) {
		super.setPurpose(otpPurpose);
		return this;
	}

	@Override
	public OtpGenerationRequestInternal setVerifyLegsCounts(Short verifyLegsCounts) {
		super.setVerifyLegsCounts(verifyLegsCounts);
		return this;
	}
}
