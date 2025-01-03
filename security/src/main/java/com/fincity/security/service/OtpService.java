package com.fincity.security.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.OtpDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.User;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;
import com.fincity.security.model.OtpGenerationRequest;
import com.fincity.security.model.OtpGenerationRequestInternal;
import com.fincity.security.model.OtpMessageVars;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.service.policy.ClientOtpPolicyService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class OtpService extends AbstractJOOQDataService<SecurityOtpRecord, ULong, Otp, OtpDAO> {

	@Autowired
	private ClientService clientService;

	@Autowired
	private AppService appService;

	@Autowired
	private UserService userService;

	@Autowired
	private EventCreationService ecService;

	@Autowired
	private MessageService messageService;

	@Autowired
	private ClientOtpPolicyService clientOtpPolicyService;

	@Autowired
	private PasswordEncoder encoder;

	private static final SecurityOtpTargetType DEFAULT_TARGET = SecurityOtpTargetType.EMAIL;

	@Override
	public Mono<Otp> create(Otp entity) {
		return this.dao.create(entity);
	}

	public Mono<Boolean> generateOtp(OtpGenerationRequest otpGenerationRequest, ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return FlatMapUtil.flatMapMono(

				() -> this.getClientAppInheritance(clientCode, appCode),

				appInherit -> {
					if (Boolean.FALSE.equals(appInherit.getT3()))
						return Mono.just(Boolean.FALSE);

					OtpGenerationRequestInternal targetReq = new OtpGenerationRequestInternal()
							.setClientOption(appInherit.getT1())
							.setAppOption(appInherit.getT2())
							.setWithoutUserOption(otpGenerationRequest.getEmailId(), otpGenerationRequest.getEmailId())
							.setIpAddress(request.getRemoteAddress())
							.setResend(otpGenerationRequest.isResend())
							.setPurpose(otpGenerationRequest.getPurpose())
					;

					return this.generateOtpInternal(targetReq);
				})
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp : [" + otpGenerationRequest.getPurpose() + "]"));
	}

	protected Mono<Boolean> generateOtpInternal(OtpGenerationRequestInternal request) {

		return FlatMapUtil.flatMapMono(

						() -> this.appService.getAppByCode(request.getAppCode()),

						app -> clientOtpPolicyService.read(request.getClientId(), app.getId()),

						(app, otpPolicy) -> {

							SecurityOtpTargetType target = SecurityOtpTargetType
									.lookupLiteral(otpPolicy.getTargetType().getLiteral());

							return target == null ? Mono.just(DEFAULT_TARGET) : Mono.just(target);
						},

						(app, otpPolicy, targetReq) -> {
							if (request.isResend() && otpPolicy.isResendSameOtp())
								return getOtpForResend(request);

							return Mono.just(Tuples.of(Boolean.TRUE, otpPolicy.generate()));
						},

						(app, otpPolicy, targetReq, otpCode) -> sendOtp(request, targetReq, otpCode.getT2()),

						(app, otpPolicy, targetReq, otpCode, otpSent) -> Boolean.TRUE.equals(otpSent)
								? this.createOtp(request, targetReq, otpCode, otpPolicy.getExpireInterval().longValue())
								.map(otpHistory -> Boolean.TRUE).onErrorReturn(Boolean.FALSE)
								: Mono.just(Boolean.FALSE))
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp"));
	}

	public Mono<Boolean> verifyOtp(String clientCode, String appCode, String emailId, String phoneNumber,
			OtpPurpose purpose, String uniqueCode) {

		if (StringUtil.safeIsBlank(clientCode) || StringUtil.safeIsBlank(appCode) || purpose == null ||
				StringUtil.safeIsBlank(uniqueCode)
				|| (StringUtil.safeIsBlank(emailId) && StringUtil.safeIsBlank(phoneNumber)))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.getClientAppInheritance(clientCode, appCode)
						.flatMap(appInherit -> Mono.justOrEmpty(
								Boolean.TRUE.equals(appInherit.getT3()) ? appInherit.getT2() : null)),

				app -> this.dao.getLatestOtp(app.getId(), emailId, phoneNumber, purpose.name()),

				(app, lotp) -> {
					if (lotp == null || lotp.isExpired() || !encoder.matches(uniqueCode, lotp.getUniqueCode()))
						return Mono.just(Boolean.FALSE);

					return this.delete(lotp.getId()).flatMap(deleted -> Mono.just(Boolean.TRUE));
				})
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtp"));
	}

	public Mono<Boolean> verifyOtpInternal(String appCode, User user, OtpPurpose purpose,
			String uniqueCode) {

		if (StringUtil.safeIsBlank(appCode) || purpose == null || StringUtil.safeIsBlank(uniqueCode))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(appCode),

				app -> this.dao.getLatestOtp(app.getId(), user.getId(), purpose.name()),

				(app, lotp) -> {
					if (lotp == null || lotp.isExpired() || !encoder.matches(uniqueCode, lotp.getUniqueCode()))
						return Mono.just(Boolean.FALSE);

					return this.delete(lotp.getId()).flatMap(deleted -> Mono.just(Boolean.TRUE));
				})
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtpInternal : [user]"));
	}

	public Mono<Boolean> verifyOtpInternal(String appCode, String emailId, String phoneNumber,
			OtpPurpose purpose, String uniqueCode) {

		if (StringUtil.safeIsBlank(appCode) || purpose == null || StringUtil.safeIsBlank(uniqueCode)
				|| (StringUtil.safeIsBlank(emailId) && StringUtil.safeIsBlank(phoneNumber)))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(appCode),

				app -> this.dao.getLatestOtp(app.getId(), emailId, phoneNumber, purpose.name()),

				(app, lotp) -> {
					if (lotp == null || lotp.isExpired() || !encoder.matches(uniqueCode, lotp.getUniqueCode()))
						return Mono.just(Boolean.FALSE);

					return this.delete(lotp.getId()).flatMap(deleted -> Mono.just(Boolean.TRUE));
				})
				.switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtpInternal : [emailId, phoneNumber]"));
	}

	private Mono<Tuple2<Boolean, String>> getOtpForResend(OtpGenerationRequestInternal request) {

		if (request.isWithUser())
			return this.dao.getLatestOtpCode(request.getAppId(), request.getUserId(),
					request.getPurpose())
					.flatMap(lastOtp -> Mono.just(Tuples.of(Boolean.TRUE, lastOtp)));

		return this.dao.getLatestOtpCode(request.getAppId(), request.getEmailId(), request.getPhoneNumber(),
				request.getPurpose())
				.flatMap(lastOtp -> Mono.just(Tuples.of(Boolean.TRUE, lastOtp)));
	}

	private Mono<Boolean> sendOtp(OtpGenerationRequestInternal request, SecurityOtpTargetType targetType, String otp) {

		if (StringUtil.safeIsBlank(otp))
			return Mono.just(Boolean.FALSE);

		boolean hasEmail = !StringUtil.safeIsBlank(request.getEmailId());
		boolean hasPhone = !StringUtil.safeIsBlank(request.getPhoneNumber());

		return switch (targetType) {
			case EMAIL -> hasEmail ? sendEmailOtp(request, otp)
					: Mono.just(Boolean.FALSE);
			case PHONE -> hasPhone ? sendPhoneOtp(request, otp)
					: Mono.just(Boolean.FALSE);
			case BOTH -> (hasEmail || hasPhone) ? sendBothOtp(request, otp)
					: Mono.just(Boolean.FALSE);
		};
	}

	private Mono<Boolean> sendBothOtp(OtpGenerationRequestInternal request, String otp) {
		return Mono.zip(
				sendEmailOtp(request, otp), sendPhoneOtp(request, otp),
				(emailSend, phoneSend) -> emailSend || phoneSend)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendBothOtp"));
	}

	private Mono<Boolean> sendEmailOtp(OtpGenerationRequestInternal request, String otp) {

		if (StringUtil.safeIsBlank(request.getEmailId()))
			return Mono.just(Boolean.FALSE);

		return this.ecService.createEvent(
				new EventQueObject()
						.setAppCode(request.getAppCode())
						.setClientCode(request.getClientCode())
						.setEventName(EventNames.USER_OTP_GENERATE)
						.setData(Map.of(
								"appName", request.getAppName(),
								"email", request.getEmailId(),
								"otp", otp,
								"otpPurpose", OtpPurpose.LOGIN)))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendEmailOtp"));
	}

	private Mono<Boolean> sendPhoneOtp(OtpGenerationRequestInternal request, String otp) {

		if (StringUtil.safeIsBlank(request.getPhoneNumber()))
			return Mono.just(Boolean.FALSE);

		return this.messageService.sendOtpMessage(
				request.getPhoneNumber(),
				new OtpMessageVars()
						.setAppName(request.getAppName())
						.setOtpCode(otp)
						.setOtpPurpose(OtpPurpose.LOGIN))
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendPhoneOtp"));
	}

	private Mono<Otp> createOtp(OtpGenerationRequestInternal request, SecurityOtpTargetType targetType, Tuple2<Boolean, String> uniqueCode, Long expireInterval) {

		Otp otp = (Otp) new Otp()
				.setAppId(request.getAppId())
				.setUserId(request.getUserId())
				.setPurpose(request.getPurpose())
				.setTargetType(targetType)
				.setUniqueCode(encoder.encode(uniqueCode.getT2()))
				.setExpiresAt(LocalDateTime.now().plusMinutes(expireInterval))
				.setIpAddress(request.getIpAddress())
				.setCreatedBy(request.getUserId())
				.setCreatedAt(LocalDateTime.now());

		return FlatMapUtil.flatMapMono(

				() -> Boolean.TRUE.equals(uniqueCode.getT1()) ? userService.increaseResendAttempt(request.getUserId())
						: Mono.just((short) 0),

				attempts -> this.create(otp))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.createOtp"));
	}

	private Mono<Tuple3<Client, App, Boolean>> getClientAppInheritance(String clientCode, String appCode) {

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientBy(clientCode),

				client -> this.appService.getAppByCode(appCode),

				(client, app) -> this.appService.appInheritance(appCode, clientCode, clientCode),

				(client, app, appInherit) -> appInherit.contains(clientCode)
						? Mono.just(Tuples.of(client, app, Boolean.TRUE))
						: Mono.just(Tuples.of(client, app, Boolean.FALSE)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.getClientAppInheritance"));
	}


}
