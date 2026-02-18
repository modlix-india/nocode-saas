package com.fincity.security.service.message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.model.otp.OtpMessageVars;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.SecurityMessageResourceService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest extends AbstractServiceUnitTest {

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private WebClient webClient;

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;

	@Mock
	private WebClient.RequestBodySpec requestBodySpec;

	@Mock
	private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

	@Mock
	private WebClient.ResponseSpec responseSpec;

	private TwoFactorService service;

	private final Gson gson = new Gson();

	@BeforeEach
	void setUp() {
		service = new TwoFactorService();
		service.setMessageResourceService(messageResourceService);
		service.setGson(gson);
		setupMessageResourceService(messageResourceService);
	}

	private void setApiKey(String key) {
		try {
			var apiKeyField = TwoFactorService.class.getDeclaredField("apiKey");
			apiKeyField.setAccessible(true);
			apiKeyField.set(service, key);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject apiKey", e);
		}
	}

	private void injectMockWebClient() {
		try {
			var webClientField = TwoFactorService.class.getDeclaredField("webClient");
			webClientField.setAccessible(true);

			// Remove final modifier
			var modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(webClientField, webClientField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

			webClientField.set(service, webClient);
		} catch (NoSuchFieldException e) {
			// In newer JDK versions, modifiers field may not be accessible;
			// use Unsafe or VarHandle approach
			try {
				var unsafeClass = Class.forName("sun.misc.Unsafe");
				var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
				unsafeField.setAccessible(true);
				var unsafe = unsafeField.get(null);

				var webClientField = TwoFactorService.class.getDeclaredField("webClient");
				var offset = (long) unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class)
						.invoke(unsafe, webClientField);
				unsafeClass.getMethod("putObject", Object.class, long.class, Object.class)
						.invoke(unsafe, service, offset, webClient);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to inject mock WebClient", ex);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject mock WebClient", e);
		}
	}

	private OtpMessageVars createOtpMessageVars() {
		return new OtpMessageVars()
				.setOtpCode("1234")
				.setOtpPurpose(OtpPurpose.LOGIN)
				.setExpireInterval(5L);
	}

	private void setupWebClientChain(ResponseEntity<String> responseEntity) {
		when(webClient.post()).thenReturn(requestBodyUriSpec);
		when(requestBodyUriSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
		doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
		doReturn(requestHeadersSpec).when(requestHeadersSpec).accept(MediaType.APPLICATION_JSON);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.toEntity(String.class)).thenReturn(Mono.just(responseEntity));
	}

	// =========================================================================
	// sendOtpMessage - dev mode (checkForKeys returns false)
	// =========================================================================

	@Nested
	@DisplayName("sendOtpMessage - dev mode")
	class DevModeTests {

		@Test
		void sendOtpMessage_ApiKeyNull_ReturnsTrueImmediately() {
			// When apiKey is null, checkForKeys returns FALSE, and the method
			// short-circuits to return Mono.just(TRUE)
			OtpMessageVars otpVars = createOtpMessageVars();

			StepVerifier.create(service.sendOtpMessage("+919999999999", otpVars))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// WebClient should never be called in dev mode
			verifyNoInteractions(webClient);
		}

		@Test
		void sendOtpMessage_ApiKeyBlank_ReturnsTrueImmediately() {
			setApiKey("   ");

			OtpMessageVars otpVars = createOtpMessageVars();

			StepVerifier.create(service.sendOtpMessage("+919999999999", otpVars))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verifyNoInteractions(webClient);
		}
	}

	// =========================================================================
	// sendOtpMessage - production mode (checkForKeys returns true)
	// =========================================================================

	@Nested
	@DisplayName("sendOtpMessage - production mode")
	class ProductionModeTests {

		@BeforeEach
		void setUpProductionMode() {
			setApiKey("valid-api-key");
			injectMockWebClient();
		}

		@Test
		void sendOtpMessage_SuccessfulApiResponse_ReturnsTrue() {
			OtpMessageVars otpVars = createOtpMessageVars();

			String successResponse = "{\"Status\":\"Success\",\"Details\":\"OTP sent\"}";
			ResponseEntity<String> responseEntity = new ResponseEntity<>(successResponse, HttpStatus.OK);
			setupWebClientChain(responseEntity);

			StepVerifier.create(service.sendOtpMessage("+919999999999", otpVars))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(webClient).post();
		}

		@Test
		void sendOtpMessage_ApiErrorStatus_ThrowsGenericException() {
			OtpMessageVars otpVars = createOtpMessageVars();

			String errorResponse = "{\"Status\":\"Error\",\"Details\":\"Invalid API key\"}";
			ResponseEntity<String> responseEntity = new ResponseEntity<>(errorResponse, HttpStatus.OK);
			setupWebClientChain(responseEntity);

			StepVerifier.create(service.sendOtpMessage("+919999999999", otpVars))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
					.verify();
		}

		@Test
		void sendOtpMessage_HttpErrorResponse_ThrowsGenericException() {
			OtpMessageVars otpVars = createOtpMessageVars();

			String errorBody = "Unauthorized";
			ResponseEntity<String> responseEntity = new ResponseEntity<>(errorBody,
					HttpStatus.UNAUTHORIZED);
			setupWebClientChain(responseEntity);

			StepVerifier.create(service.sendOtpMessage("+919999999999", otpVars))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
					.verify();
		}
	}

	// =========================================================================
	// TransactionalSms.toBodyMap
	// =========================================================================

	@Nested
	@DisplayName("TransactionalSms.toBodyMap")
	class TransactionalSmsTests {

		@Test
		void toBodyMap_ContainsRequiredFields() {
			TwoFactorService.TransactionalSms sms = TwoFactorService.TransactionalSms.builder()
					.apikey("test-key")
					.to(new String[] { "+919999999999" })
					.templateName("TEMPLATE")
					.var1("login")
					.var2("1234")
					.var3("5mins")
					.build();

			var bodyMap = sms.toBodyMap();

			assertEquals("TRANS_SMS", bodyMap.getFirst("module"));
			assertEquals("test-key", bodyMap.getFirst("apikey"));
			assertEquals("+919999999999", bodyMap.getFirst("to"));
			assertEquals("APYAFI", bodyMap.getFirst("from"));
			assertEquals("TEMPLATE", bodyMap.getFirst("templatename"));
			assertEquals("login", bodyMap.getFirst("var1"));
			assertEquals("1234", bodyMap.getFirst("var2"));
			assertEquals("5mins", bodyMap.getFirst("var3"));
		}
	}
}
