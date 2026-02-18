package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.OneTimeTokenDAO;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OneTimeTokenServiceTest {

	@Mock
	private OneTimeTokenDAO dao;

	@InjectMocks
	private OneTimeTokenService service;

	private static final ULong TOKEN_ID = ULong.valueOf(1);
	private static final ULong USER_ID = ULong.valueOf(10);

	@BeforeEach
	void setUp() {
		// Inject the mocked DAO via reflection since AbstractJOOQDataService stores
		// dao in a superclass field.
		// OneTimeTokenService -> AbstractJOOQDataService (has dao) -> 1 getSuperclass()
		try {
			var daoField = service.getClass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_GeneratesUUIDToken() {
			OneTimeToken input = new OneTimeToken();
			input.setUserId(USER_ID);

			when(dao.create(any(OneTimeToken.class))).thenAnswer(invocation -> {
				OneTimeToken arg = invocation.getArgument(0);
				// Verify token has been set
				assertNotNull(arg.getToken());
				// Return the entity with an ID
				arg.setId(TOKEN_ID);
				return Mono.just(arg);
			});

			StepVerifier.create(service.create(input))
					.assertNext(result -> {
						assertNotNull(result.getToken());
						assertEquals(TOKEN_ID, result.getId());
						assertEquals(USER_ID, result.getUserId());
						// UUID without dashes is 32 hex characters
						assertEquals(32, result.getToken().length());
					})
					.verifyComplete();

			verify(dao).create(any(OneTimeToken.class));
		}

		@Test
		void create_TokenHasNoDashes() {
			OneTimeToken input = new OneTimeToken();
			input.setUserId(USER_ID);

			when(dao.create(any(OneTimeToken.class))).thenAnswer(invocation -> {
				OneTimeToken arg = invocation.getArgument(0);
				arg.setId(TOKEN_ID);
				return Mono.just(arg);
			});

			StepVerifier.create(service.create(input))
					.assertNext(result -> {
						assertNotNull(result.getToken());
						assertFalse(result.getToken().contains("-"),
								"Token should not contain dashes");
						// Verify it's a valid hex string (32 chars without dashes)
						assertTrue(result.getToken().matches("[0-9a-f]{32}"),
								"Token should be 32 hex characters");
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getOneTimeToken
	// =========================================================================

	@Nested
	@DisplayName("getOneTimeToken")
	class GetOneTimeTokenTests {

		@Test
		void getOneTimeToken_ReadsAndDeletes() {
			String tokenValue = "abc123def456abc123def456abc12345";

			OneTimeToken found = TestDataFactory.createOneTimeToken(TOKEN_ID, USER_ID, tokenValue);

			when(dao.readOneTimeTokenAndDeleteBy(tokenValue)).thenReturn(Mono.just(found));

			StepVerifier.create(service.getOneTimeToken(tokenValue))
					.assertNext(result -> {
						assertEquals(USER_ID, result.getUserId());
						assertEquals(tokenValue, result.getToken());
					})
					.verifyComplete();

			verify(dao).readOneTimeTokenAndDeleteBy(tokenValue);
		}

		@Test
		void getOneTimeToken_NotFound_ReturnsEmpty() {
			String tokenValue = "nonexistenttoken12345678901234";

			when(dao.readOneTimeTokenAndDeleteBy(tokenValue)).thenReturn(Mono.empty());

			StepVerifier.create(service.getOneTimeToken(tokenValue))
					.verifyComplete();

			verify(dao).readOneTimeTokenAndDeleteBy(tokenValue);
		}
	}
}
