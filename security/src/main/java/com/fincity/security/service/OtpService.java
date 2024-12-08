package com.fincity.security.service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.OtpDAO;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.User;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.OtpMessageVars;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.util.HashUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

@Service
public class OtpService extends AbstractJOOQUpdatableDataService<SecurityOtpRecord, ULong, Otp, OtpDAO> {

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

	@Value("${message.otp.expire.interval:15}")
	private long otpExpireInterval;

	@Override
	public Mono<Otp> create(Otp entity) {
		return this.dao.create(entity);
	}

	@Override
	protected Mono<Otp> updatableEntity(Otp entity) {
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return null;
	}

	@Override
	public Mono<Otp> update(Otp entity) {
		return this.dao.update(entity);
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

				(tup, linCCheck, app) -> {

					ULong clientId = app.getClientId();
					return this.appService.getProperties(app.getClientId(), app.getId(), app.getAppCode(), null).map(
							e -> {
								if (e.isEmpty() || !e.containsKey(clientId))
									return Tuples.of("", "", "", "", "");

								Map<String, AppProperty> clientProp = e.get(clientId);

								if (clientProp.containsKey(AppService.APP_PROP_LOGIN_TYPE))
									return getRequiredOtpProp(clientProp);

								return e.values().stream().findFirst()
										.map(this::getRequiredOtpProp)
										.orElseGet(() -> Tuples.of("", "", "", "", ""));
							});
				},

				(tup, linCCheck, app, prop) -> {

					if (StringUtil.safeIsBlank(prop.getT1())
							|| !prop.getT1().equals(AppService.APP_PROP_LOGIN_TYPE_OTP))
						return messageResourceService.getMessage(SecurityMessageResourceService.INVALID_APP_PROP)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.BAD_REQUEST, msg)));

					SecurityOtpTargetType securityOtpTargetType = SecurityOtpTargetType.lookupLiteral(prop.getT3());

					if (securityOtpTargetType == null)
						return messageResourceService.getMessage(SecurityMessageResourceService.INVALID_APP_PROP)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.BAD_REQUEST, msg)));

					return Mono.just(Tuples.of(securityOtpTargetType, ""));
				},

				(tup, linCCheck, app, prop, otp) -> {

					if (Boolean.parseBoolean(prop.getT4()))
						// if we have a const prop we will not sent it just create a entry for that.
						return Mono.just(Boolean.TRUE);

					return sendOtp(otp.getT1(), tup.getT3(), otp.getT2(), app.getAppCode(), clientCode,
							app.getAppName());
				},

				(tup, linCCheck, app, prop, otp, otpSent) -> {
					if (Boolean.TRUE.equals(otpSent))
						return this
								.createOtp(app.getId(), tup.getT3(), OtpPurpose.LOGIN.name(), otp.getT1(), otp.getT2(),
										request.getRemoteAddress())
								.map(otpHistory -> otpHistory.getId() != null ? Boolean.TRUE : Boolean.FALSE);
					return Mono.just(Boolean.FALSE);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp"));
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

					if (lotp.match(uniqueCode))
						return Mono.just(Boolean.TRUE);

					return Mono.just(Boolean.FALSE);
				});
	}

	private Tuple5<String, String, String, String, String> getRequiredOtpProp(Map<String, AppProperty> appProperties) {
		return Tuples.of(
				appProperties.get(AppService.APP_PROP_LOGIN_TYPE).getValue(),
				appProperties.get(AppService.APP_PROP_OTP_TYPE).getValue(),
				appProperties.get(AppService.APP_PROP_OTP_TYPE_TARGET_TYPE).getValue(),
				appProperties.get(AppService.APP_PROP_OTP_TYPE_USE_CONST).getValue(),
				appProperties.get(AppService.APP_PROP_OTP_TYPE_CONST).getValue());
	}

	private Mono<Boolean> sendOtp(SecurityOtpTargetType securityOtpTargetType, User user,
			String otp, String appCode, String clientCode, String appName) {

		if (StringUtil.safeIsBlank(otp))
			return Mono.just(false);

		boolean hasEmail = !StringUtil.safeIsBlank(user.getEmailId());
		boolean hasPhone = !StringUtil.safeIsBlank(user.getPhoneNumber());

		return switch (securityOtpTargetType) {
			case EMAIL -> hasEmail ? sendEmailOtp(appCode, clientCode, appName, user.getEmailId(), otp)
					: Mono.just(false);
			case PHONE -> hasPhone ? sendPhoneOtp(appName, user.getPhoneNumber(), otp)
					: Mono.just(false);
			case BOTH -> (hasEmail || hasPhone) ? sendBothOtp(appCode, clientCode, appName, user, otp)
					: Mono.just(false);
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
			return Mono.just(false);

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
			return Mono.just(false);

		return this.messageService.sendOtpMessage(
				phoneNumber,
				new OtpMessageVars()
						.setAppName(appName)
						.setOtpCode(otp)
						.setOtpPurpose(OtpPurpose.LOGIN))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendPhoneOtp"));
	}

	public Mono<Otp> createOtp(ULong appId, User user, String purpose, SecurityOtpTargetType targetType,
			String uniqueCode, InetSocketAddress ipAddress) {

		Otp otp = new Otp()
				.setAppId(appId)
				.setUserId(user.getId())
				.setPurpose(purpose)
				.setTargetType(targetType)
				.setUniqueCode(HashUtil.hash(uniqueCode))
				.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpireInterval))
				.setIpAddress(ipAddress != null ? ipAddress.getHostString() : null);

		return super.create(otp);
	}
}
