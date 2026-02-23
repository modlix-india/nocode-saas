package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.model.otp.OtpGenerationRequest;
import com.fincity.security.model.otp.OtpVerificationRequest;
import com.fincity.security.service.OtpService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { OtpController.class, TestWebSecurityConfig.class })
class OtpControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private OtpService otpService;

	@Test
	@DisplayName("POST /api/security/otp/generate - Should return true when OTP is generated successfully")
	void generateOtp_validRequest_returnsTrue() {

		when(otpService.generateOtp(any(OtpGenerationRequest.class), any()))
				.thenReturn(Mono.just(Boolean.TRUE));

		OtpGenerationRequest request = new OtpGenerationRequest()
				.setEmailId("user@example.com")
				.setPurpose(OtpPurpose.LOGIN);

		webTestClient.post()
				.uri("/api/security/otp/generate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Boolean.class).isEqualTo(true);

		verify(otpService).generateOtp(any(OtpGenerationRequest.class), any());
	}

	@Test
	@DisplayName("POST /api/security/otp/verify - Should return true when OTP verification succeeds")
	void verifyOtp_validOtp_returnsTrue() {

		when(otpService.verifyOtp(any(OtpVerificationRequest.class)))
				.thenReturn(Mono.just(Boolean.TRUE));

		OtpVerificationRequest request = new OtpVerificationRequest()
				.setEmailId("user@example.com")
				.setPurpose(OtpPurpose.LOGIN)
				.setOtp("123456");

		webTestClient.post()
				.uri("/api/security/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Boolean.class).isEqualTo(true);

		verify(otpService).verifyOtp(any(OtpVerificationRequest.class));
	}

	@Test
	@DisplayName("POST /api/security/otp/verify - Should return false when OTP verification fails")
	void verifyOtp_invalidOtp_returnsFalse() {

		when(otpService.verifyOtp(any(OtpVerificationRequest.class)))
				.thenReturn(Mono.just(Boolean.FALSE));

		OtpVerificationRequest request = new OtpVerificationRequest()
				.setEmailId("user@example.com")
				.setPurpose(OtpPurpose.LOGIN)
				.setOtp("000000");

		webTestClient.post()
				.uri("/api/security/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Boolean.class).isEqualTo(false);

		verify(otpService).verifyOtp(any(OtpVerificationRequest.class));
	}
}
