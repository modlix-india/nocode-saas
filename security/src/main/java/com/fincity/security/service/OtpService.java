package com.fincity.security.service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.OtpDAO;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.User;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.OtpMessageVars;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.service.policy.ClientOtpPolicyService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class OtpService extends AbstractJOOQDataService<SecurityOtpRecord, ULong, Otp, OtpDAO> {

	@Autowired
	private AppService appService;

	@Autowired
	private UserService userService;

	@Autowired
	private EventCreationService ecService;

	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@Autowired
	private MessageService messageService;

	@Autowired
	private ClientOtpPolicyService clientOtpPolicyService;

	@Autowired
	private PasswordEncoder encoder;

	@Override
	public Mono<Otp> create(Otp entity) {
		return this.dao.create(entity);
	}

	public Mono<Boolean> generateOtp(AuthenticationRequest authRequest, ServerHttpRequest request) {

		if (!authRequest.isGenerateOtp())
			return Mono.just(false);

		String appCode = request.getHeaders()
				.getFirst("appCode");

		String clientCode = request.getHeaders()
				.getFirst("clientCode");

		if (authRequest.getIdentifierType() == null) {
			authRequest.setIdentifierType(StringUtil.safeIsBlank(authRequest.getUserName()) || authRequest.getUserName()
					.indexOf('@') == -1 ? AuthenticationIdentifierType.USER_NAME
							: AuthenticationIdentifierType.EMAIL_ID);
		}

		return FlatMapUtil.flatMapMono(
				() -> this.userService.findUserNClient(authRequest.getUserName(), authRequest.getUserId(),
						clientCode, appCode, authRequest.getIdentifierType(), true),

				tup -> {
					String linClientCode = tup.getT1().getCode();
					return Mono.justOrEmpty(linClientCode.equals("SYSTEM") || clientCode.equals(linClientCode)
							|| tup.getT1().getId().equals(tup.getT2().getId()) ? true : null);
				},

				(tup, linCCheck) -> this.appService.getAppByCode(appCode),

				(tup, linCCheck, app) -> clientOtpPolicyService.getClientAppPolicy(app.getClientId(), app.getId())
						.flatMap(policy -> Mono.just(Tuples.of(policy.getTargetType(), policy.getExpireInterval().longValue(), policy.generate())))
						.switchIfEmpty(
								messageResourceService.getMessage(SecurityMessageResourceService.APP_POLICY_EMPTY)
										.flatMap(msg -> Mono.error(new GenericException(HttpStatus.BAD_REQUEST, msg)))),

				(tup, linCCheck, app, otpPolicy) -> {

					SecurityOtpTargetType target = SecurityOtpTargetType.lookupLiteral(otpPolicy.getT1().getLiteral());

					return target != null ? Mono.just(target) : Mono.just(SecurityOtpTargetType.EMAIL);
				},

				(tup, linCCheck, app, otpPolicy, target) -> sendOtp(target, tup.getT3(), otpPolicy.getT3(),
						app.getAppCode(), clientCode, app.getAppName()),

				(tup, linCCheck, app, otpPolicy, target, otpSent) -> Boolean.TRUE.equals(otpSent)
						? this.createOtp(app.getId(), tup.getT3(), OtpPurpose.LOGIN.name(), target, otpPolicy.getT3(),
								otpPolicy.getT2(), request.getRemoteAddress())
								.map(otpHistory -> Boolean.TRUE)
								.onErrorReturn(Boolean.FALSE)
						: Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp"));
	}

	public Mono<Boolean> verifyOtp(String appCode, User user, String purpose, String uniqueCode) {

		if (StringUtil.safeIsBlank(uniqueCode))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(appCode),

				app -> this.dao.getLatestOtp(app.getId(), user.getId(), purpose),
				(app, lotp) -> {
					if (lotp == null)
						return Mono.just(Boolean.FALSE);

					if (lotp.isExpired())
						return Mono.just(Boolean.FALSE);

					if (encoder.matches(uniqueCode, lotp.getUniqueCode()))
						return Mono.just(Boolean.TRUE);

					return Mono.just(Boolean.FALSE);
				})
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtp"));
	}

	private Mono<Boolean> sendOtp(SecurityOtpTargetType securityOtpTargetType, User user,
			String otp, String appCode, String clientCode, String appName) {

		if (StringUtil.safeIsBlank(otp))
			return Mono.just(Boolean.FALSE);

		boolean hasEmail = !StringUtil.safeIsBlank(user.getEmailId());
		boolean hasPhone = !StringUtil.safeIsBlank(user.getPhoneNumber());

		return switch (securityOtpTargetType) {
			case EMAIL -> hasEmail ? sendEmailOtp(appCode, clientCode, appName, user.getEmailId(), otp)
					: Mono.just(Boolean.FALSE);
			case PHONE -> hasPhone ? sendPhoneOtp(appName, user.getPhoneNumber(), otp)
					: Mono.just(Boolean.FALSE);
			case BOTH -> (hasEmail || hasPhone) ? sendBothOtp(appCode, clientCode, appName, user, otp)
					: Mono.just(Boolean.FALSE);
		};
	}

	private Mono<Boolean> sendBothOtp(String appCode, String clientCode, String appName,
			User user, String otp) {
		return Mono.zip(
				sendEmailOtp(appCode, clientCode, appName,
						!StringUtil.safeIsBlank(user.getEmailId()) ? user.getEmailId() : null, otp),
				sendPhoneOtp(appName, !StringUtil.safeIsBlank(user.getPhoneNumber()) ? user.getPhoneNumber() : null,
						otp),
				(emailSend, phoneSend) -> emailSend || phoneSend)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendBothOtp"));
	}

	private Mono<Boolean> sendEmailOtp(String appCode, String clientCode, String appName, String email, String otp) {

		if (email == null)
			return Mono.just(Boolean.FALSE);

		return this.ecService.createEvent(
				new EventQueObject()
						.setAppCode(appCode)
						.setClientCode(clientCode)
						.setEventName(EventNames.USER_OTP_GENERATE)
						.setData(Map.of(
								"appName", appName,
								"email", email,
								"otp", otp,
								"otpPurpose", OtpPurpose.LOGIN)))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendEmailOtp"));
	}

	private Mono<Boolean> sendPhoneOtp(String appName, String phoneNumber, String otp) {

		if (phoneNumber == null)
			return Mono.just(Boolean.FALSE);

		return this.messageService.sendOtpMessage(
				phoneNumber,
				new OtpMessageVars()
						.setAppName(appName)
						.setOtpCode(otp)
						.setOtpPurpose(OtpPurpose.LOGIN))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendPhoneOtp"));
	}

	private Mono<Otp> createOtp(ULong appId, User user, String purpose, SecurityOtpTargetType targetType,
			String uniqueCode, Long expireInterval, InetSocketAddress ipAddress) {

		Otp otp = new Otp()
				.setAppId(appId)
				.setUserId(user.getId())
				.setPurpose(purpose)
				.setTargetType(targetType)
				.setUniqueCode(encoder.encode(uniqueCode))
				.setExpiresAt(LocalDateTime.now().plusMinutes(expireInterval))
				.setIpAddress(ipAddress != null ? ipAddress.getHostString() : null);

		return super.create(otp)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.createOtp"));
	}
}
