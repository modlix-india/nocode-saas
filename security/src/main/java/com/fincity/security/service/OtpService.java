package com.fincity.security.service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
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
import com.fincity.security.enums.otp.OtpType;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class OtpService extends AbstractJOOQUpdatableDataService<SecurityOtpRecord, ULong, Otp, OtpDAO> {

	@Autowired
	private AppService appService;

	@Autowired
	private UserService userService;

	@Autowired
	private EventCreationService ecService;

	@Value("${otp.phone.expire.interval:15}")
	private long otpExpireInterval;

	@Value("${otp.phone.retry.limit:5}")
	private long otpRetryLimit;

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
					return this.appService.getProperties(app.getClientId(), app.getId(), app.getAppCode(),
							AppService.APP_PROP_LOGIN_TYPE).map(
							e -> {
								if (e.isEmpty() || !e.containsKey(clientId))
									return Tuples.of("", "");

								Map<String, AppProperty> clientProp = e.get(clientId);

								if (clientProp.containsKey(AppService.APP_PROP_LOGIN_TYPE)) {
									return Tuples.of(
											clientProp.get(AppService.APP_PROP_LOGIN_TYPE).getValue(),
											clientProp.get(AppService.APP_PROP_OTP_TYPE).getValue());
								}

								return e.values().stream().findFirst()
										.map(prop -> Tuples.of(
												prop.get(AppService.APP_PROP_LOGIN_TYPE).getValue(),
												prop.get(AppService.APP_PROP_OTP_TYPE).getValue()))
										.orElseGet(() -> Tuples.of("", ""));
							});
				},

				(tup, linCCheck, app, prop) -> {

					if (StringUtil.safeIsBlank(prop.getT1())
							|| prop.getT1().equals(AppService.APP_PROP_LOGIN_TYPE_OTP)) {
						return Mono.empty();
					}

					OtpType otpType = OtpType.fromName(prop.getT2(), OtpType.NUMERIC_4);

					SecurityOtpTargetType securityOtpTargetType = SecurityOtpTargetType.lookupLiteral(
							prop.getT1().substring((AppService.APP_PROP_LOGIN_TYPE_OTP + "_").length()));

					if (securityOtpTargetType == null) {
						securityOtpTargetType = SecurityOtpTargetType.PHONE;
					}

					return Mono.just(Tuples.of(securityOtpTargetType, otpType.generateOtp()));
				},

				(tup, linCCheck, app, prop, otp) -> sendOtp(otp.getT1(), otp.getT2(), app.getAppCode(),
						clientCode, app.getAppName()),

				(tup, linCCheck, app, prop, otp, otpSent) -> {
					if (Boolean.TRUE.equals(otpSent)) {
						return this
								.createOtp(app.getId(), tup.getT3(), OtpPurpose.LOGIN.name(), otp.getT1(), otp.getT2(),
										request.getRemoteAddress())
								.map(otpHistory -> otpHistory.getId() != null ? Boolean.TRUE : Boolean.FALSE);
					}
					return Mono.just(Boolean.FALSE);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp"));
	}

	public Mono<Boolean> verifyOtp(String appCode, User user, String purpose,
	                               String uniqueCode) {

		if (StringUtil.safeIsBlank(uniqueCode))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(appCode),

				app -> this.dao.getLatestOtp(app.getId(), user.getId(), purpose),
				(app, lotp) -> {
					if (lotp == null)
						return Mono.just(Boolean.FALSE);

					if (lotp.isExpired()) {
						return Mono.just(Boolean.FALSE);
					}

					if (lotp.match(uniqueCode))
						return Mono.just(Boolean.TRUE);

					return Mono.just(Boolean.FALSE);
				},
				(app, lotp, isvalid) -> this.update(lotp),
				(app, lotp, isValid, uOtp) -> Mono.just(isValid));
	}

	private Mono<Boolean> sendOtp(SecurityOtpTargetType securityOtpTargetType, String otp, String appCode,
	                              String clientCode, String appName) {

		if (StringUtil.safeIsBlank(otp))
			return Mono.just(false);

		return switch (securityOtpTargetType) {
			case EMAIL -> sendEmailOtp(appCode, clientCode, appName, otp);
			case PHONE -> sendPhoneOtp(appCode, clientCode, appName, otp);
			case BOTH -> sendBothOtp(appCode, clientCode, appName, otp);
		};
	}

	private Mono<Boolean> sendBothOtp(String appCode, String clientCode, String appName, String otp) {
		return Mono.zip(sendEmailOtp(appCode, clientCode, appName, otp),
						sendPhoneOtp(appCode, clientCode, appName, otp),
						(emailSend, phoneSend) -> emailSend || phoneSend)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendBothOtp"));
	}

	private Mono<Boolean> sendEmailOtp(String appCode, String clientCode, String appName, String otp) {
		return this.ecService.createEvent(
						new EventQueObject()
								.setAppCode(appCode)
								.setClientCode(clientCode)
								.setEventName(EventNames.USER_OTP_GENERATE)
								.setData(Map.of(
										"appName", appName,
										"otp", otp)))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendEmailOtp"));
	}

	private Mono<Boolean> sendPhoneOtp(String appCode, String clientCode, String appName, String otp) {

		// TODO
		return Mono.just(Boolean.FALSE)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendPhoneOtp"));
	}

	public Mono<Otp> createOtp(ULong appId, User user, String purpose, SecurityOtpTargetType targetType,
	                           String uniqueCode, InetSocketAddress ipAddress) {

		Otp otp = new Otp()
				.setAppId(appId)
				.setUserId(user.getId())
				.setPurpose(purpose)
				.setTargetType(targetType)
				.setUniqueCode(uniqueCode)
				.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpireInterval))
				.setIpAddress(ipAddress != null ? ipAddress.getHostString() : null);

		return super.create(otp);
	}
}
