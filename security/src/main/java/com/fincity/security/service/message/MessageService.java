package com.fincity.security.service.message;

import com.fincity.security.model.OtpMessageVars;

import reactor.core.publisher.Mono;

public interface MessageService {

	Mono<Boolean> sendOtpMessage(String phoneNumber, OtpMessageVars otpMessageVars);
}
