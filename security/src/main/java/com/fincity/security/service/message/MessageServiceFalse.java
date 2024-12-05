package com.fincity.security.service.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fincity.security.model.OtpMessageVars;

import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "sms.otp.allow", havingValue = "false", matchIfMissing = true)
public class MessageServiceFalse implements MessageService {

	private static final Logger logger = LoggerFactory.getLogger(MessageServiceFalse.class);

	@Override
	public Mono<Boolean> sendOtpMessage(String phoneNumber, OtpMessageVars otpMessageVars) {
		logger.info("Sending fOTP message to {} with details {}", phoneNumber, otpMessageVars);
		return Mono.just(Boolean.TRUE);
	}
}
