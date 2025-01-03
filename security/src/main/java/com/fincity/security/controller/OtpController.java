package com.fincity.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.service.OtpService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/otp")
public class OtpController {

	private final OtpService otpService;

	public OtpController(OtpService otpService) {
		this.otpService = otpService;
	}

	@PostMapping("/generate")
	public Mono<ResponseEntity<Boolean>> generateOtp(
			@RequestParam(required = false) String emailId,
			@RequestParam(required = false) String phoneNumber,
			@RequestParam(defaultValue = "false") boolean isResend,
			@RequestParam OtpPurpose purpose,
			ServerHttpRequest request) {

		return this.otpService.generateOtp(emailId, phoneNumber, purpose, isResend, request).map(ResponseEntity::ok);
	}

	@PostMapping("/verify")
	public Mono<ResponseEntity<Boolean>> generateOtp(
			@RequestParam(required = false) String emailId,
			@RequestParam(required = false) String phoneNumber,
			@RequestParam OtpPurpose purpose,
			@RequestParam String otp,
			ServerHttpRequest request) {

		String clientCode = request.getHeaders().getFirst("clientCode");
		String appCode = request.getHeaders().getFirst("appCode");

		return this.otpService.verifyOtp(clientCode, appCode, emailId, phoneNumber, purpose, otp).map(ResponseEntity::ok);
	}
}
