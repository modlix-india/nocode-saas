package com.fincity.security.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
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
import com.fincity.security.model.otp.OtpGenerationRequest;
import com.fincity.security.model.otp.OtpGenerationRequestInternal;
import com.fincity.security.model.otp.OtpMessageVars;
import com.fincity.security.model.otp.OtpVerificationRequest;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.service.policy.ClientOtpPolicyService;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class OtpService extends AbstractJOOQUpdatableDataService<SecurityOtpRecord, ULong, Otp, OtpDAO> {

    private final ClientService clientService;

    private final AppService appService;

    private final EventCreationService ecService;

    private final MessageService messageService;

    private final ClientOtpPolicyService clientOtpPolicyService;

    private final PasswordEncoder encoder;

    @Getter
    private UserService userService;

    public OtpService(ClientService clientService, AppService appService, EventCreationService ecService,
                      MessageService messageService, ClientOtpPolicyService clientOtpPolicyService, PasswordEncoder encoder) {
        this.clientService = clientService;
        this.appService = appService;
        this.ecService = ecService;
        this.messageService = messageService;
        this.clientOtpPolicyService = clientOtpPolicyService;
        this.encoder = encoder;
    }

    @Autowired
    public void setUserService(@Lazy UserService userService) {
        this.userService = userService;
    }

    private static final SecurityOtpTargetType DEFAULT_TARGET = SecurityOtpTargetType.EMAIL;

    @Override
    public Mono<Otp> create(Otp entity) {
        return this.dao.create(entity);
    }

    @Override
    protected Mono<Otp> updatableEntity(Otp entity) {
        return this.read(entity.getId())
                .map(e -> e.setVerifyLegsCounts(entity.getVerifyLegsCounts()));
    }

    public Mono<Boolean> generateOtp(OtpGenerationRequest otpGenerationRequest, ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.getClientAppInheritance(ca.getUrlClientCode(), ca.getUrlAppCode()),

                        (ca, appInherit) -> {
                            if (Boolean.FALSE.equals(appInherit.getT3()))
                                return Mono.just(Boolean.FALSE);

                            OtpGenerationRequestInternal targetReq = new OtpGenerationRequestInternal()
                                    .setClientOption(appInherit.getT1())
                                    .setAppOption(appInherit.getT2())
                                    .setWithoutUserOption(otpGenerationRequest.getEmailId(),
                                            otpGenerationRequest.getPhoneNumber())
                                    .setIpAddress(request.getRemoteAddress())
                                    .setResend(otpGenerationRequest.isResend())
                                    .setPurpose(otpGenerationRequest.getPurpose());

                            if (ca.isAuthenticated())
                                targetReq.setUserId(ULongUtil.valueOf(ca.getUser().getId()));

                            return this.generateOtpInternal(targetReq);
                        })
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "OtpService.generateOtp : [OtpGenerationRequest, ServerHttpRequest]"));
    }

    public Mono<Boolean> generateOtpInternal(OtpGenerationRequestInternal request) {

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.getAppByCode(request.getAppCode()),

                        app -> clientOtpPolicyService.getClientAppPolicy(request.getClientId(), app.getId()),

                        (app, otpPolicy) -> {

                            SecurityOtpTargetType target = SecurityOtpTargetType
                                    .lookupLiteral(otpPolicy.getTargetType().getLiteral());

                            return target == null ? Mono.just(DEFAULT_TARGET) : Mono.just(target);
                        },

                        (app, otpPolicy, target) -> {
                            if (request.isResend() && otpPolicy.isResendSameOtp())
                                return getOtpForResend(request);

                            return Mono.just(otpPolicy.generate());
                        },

                        (app, otpPolicy, target, otp) -> sendOtp(request, target, otpPolicy.getExpireInterval(),
                                otp),

                        (app, otpPolicy, target, otp, otpSent) -> Boolean.TRUE.equals(otpSent)
                                ? this.createOtpEntity(request, target, otp, otpPolicy.getExpireInterval())
                                : Mono.just(Boolean.FALSE))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtpInternal"));
    }

    private Mono<String> getOtpForResend(OtpGenerationRequestInternal request) {

        if (request.isWithUser())
            return this.dao.getLatestOtpCode(request.getAppId(), request.getUserId(), request.getPurpose());

        return this.dao.getLatestOtpCode(request.getAppId(), request.getEmailId(), request.getPhoneNumber(),
                request.getPurpose());
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

    private Mono<Boolean> sendOtp(OtpGenerationRequestInternal request, SecurityOtpTargetType targetType,
                                  Long expireInterval, String otp) {

        if (StringUtil.safeIsBlank(otp))
            return Mono.just(Boolean.FALSE);

        boolean hasEmail = !StringUtil.safeIsBlank(request.getEmailId());
        boolean hasPhone = !StringUtil.safeIsBlank(request.getPhoneNumber());

        return switch (targetType) {
            case EMAIL -> hasEmail ? sendEmailOtp(request, expireInterval, otp)
                    : Mono.just(Boolean.FALSE);
            case PHONE -> hasPhone ? sendPhoneOtp(request, expireInterval, otp)
                    : Mono.just(Boolean.FALSE);
            case BOTH -> (hasEmail || hasPhone) ? sendBothOtp(request, expireInterval, otp)
                    : Mono.just(Boolean.FALSE);
        };
    }

    private Mono<Boolean> sendBothOtp(OtpGenerationRequestInternal request, Long expireInterval, String otp) {
        return Mono.zip(
                        sendEmailOtp(request, expireInterval, otp), sendPhoneOtp(request, expireInterval, otp),
                        (emailSend, phoneSend) -> emailSend || phoneSend)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendBothOtp"));
    }

    private Mono<Boolean> sendEmailOtp(OtpGenerationRequestInternal request, Long expireInterval, String otp) {

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
                                        "otpPurpose", request.getPurpose(),
                                        "expireInterval", expireInterval)))
                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendEmailOtp"));
    }

    private Mono<Boolean> sendPhoneOtp(OtpGenerationRequestInternal request, Long expireInterval, String otp) {

        if (StringUtil.safeIsBlank(request.getPhoneNumber()))
            return Mono.just(Boolean.FALSE);

        return this.messageService.sendOtpMessage(
                        request.getPhoneNumber(),
                        new OtpMessageVars()
                                .setOtpCode(otp)
                                .setOtpPurpose(request.getPurpose())
                                .setExpireInterval(expireInterval))
                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.sendPhoneOtp"));
    }

    private Mono<Boolean> createOtpEntity(OtpGenerationRequestInternal request, SecurityOtpTargetType targetType,
                                          String uniqueCode, Long expireInterval) {

        Otp otpEntity = (Otp) new Otp()
                .setAppId(request.getAppId())
                .setUserId(request.getUserId())
                .setPurpose(request.getPurpose().name())
                .setTargetType(targetType)
                .setTargetOptions(request.getEmailId(), request.getPhoneNumber())
                .setUniqueCode(encoder.encode(uniqueCode))
                .setExpiresAt(LocalDateTime.now().plusMinutes(expireInterval))
                .setIpAddress(request.getIpAddress())
                .setCreatedBy(request.getUserId())
                .setCreatedAt(LocalDateTime.now());

        return FlatMapUtil.flatMapMono(

                        () -> request.isResend() && request.isWithUser()
                                ? userService.increaseResendAttempt(request.getUserId())
                                : Mono.just((short) 0),

                        attempts -> this.create(otpEntity),
                        (attempts, created) -> Mono.just(Boolean.TRUE))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.createOtp"));
    }

    public Mono<Boolean> verifyOtp(OtpVerificationRequest otpVerificationRequest) {

        if (otpVerificationRequest.isNotValid())
            return Mono.just(Boolean.FALSE);

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.getClientAppInheritance(ca.getUrlClientCode(), ca.getUrlAppCode())
                                .flatMap(appInherit -> Mono.justOrEmpty(
                                        Boolean.TRUE.equals(appInherit.getT3()) ? appInherit.getT2() : null)),

                        (ca, app) -> this.dao.getLatestOtp(app.getId(), otpVerificationRequest.getEmailId(),
                                otpVerificationRequest.getPhoneNumber(), otpVerificationRequest.getPurpose()),

                        (ca, app, lotp) -> verifyOtp(lotp, otpVerificationRequest.getOtp(),
                                otpVerificationRequest.getPurpose()))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtp"));
    }

    public Mono<Boolean> verifyOtpInternal(String appCode, User user, OtpVerificationRequest otpVerificationRequest) {

        if (StringUtil.safeIsBlank(appCode) || otpVerificationRequest.isOtpNotValid())
            return Mono.just(Boolean.FALSE);

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.getAppByCode(appCode),

                        app -> this.dao.getLatestOtp(app.getId(), user.getId(), otpVerificationRequest.getPurpose()),

                        (app, lotp) -> verifyOtp(lotp, otpVerificationRequest.getOtp(), otpVerificationRequest.getPurpose()))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtpInternal : [user]"));
    }

    public Mono<Boolean> verifyOtpInternal(String appCode, OtpVerificationRequest otpVerificationRequest) {

        if (StringUtil.safeIsBlank(appCode) || otpVerificationRequest.isNotValid())
            return Mono.just(Boolean.FALSE);

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.getAppByCode(appCode),

                        app -> this.dao.getLatestOtp(app.getId(), otpVerificationRequest.getEmailId(),
                                otpVerificationRequest.getPhoneNumber(), otpVerificationRequest.getPurpose()),

                        (app, lotp) -> verifyOtp(lotp, otpVerificationRequest.getOtp(), otpVerificationRequest.getPurpose()))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.verifyOtpInternal : [emailId, phoneNumber]"));
    }

    private Mono<Boolean> verifyOtp(Otp latestOtp, String otp, OtpPurpose purpose) {

        if (latestOtp == null || latestOtp.isExpired() || !encoder.matches(otp, latestOtp.getUniqueCode()))
            return Mono.just(Boolean.FALSE);

        return latestOtp.getVerifyLegsCounts().equals(purpose.getVerifyLegsCounts())
                ? this.delete(latestOtp.getId()).flatMap(deleted -> Mono.just(Boolean.TRUE))
                : this.dao.increaseVerifyCounts(latestOtp.getId()).flatMap(updated -> Mono.just(Boolean.TRUE));
    }
}
