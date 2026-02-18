package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest extends AbstractServiceUnitTest {

	@Mock
	private TokenDAO dao;

	@Mock
	private CacheService cacheService;

	private TokenService service;

	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong TOKEN_ID = ULong.valueOf(500);

	@BeforeEach
	void setUp() {
		service = new TokenService(cacheService);

		// TokenService -> AbstractJOOQDataService (has dao)
		// 1 getSuperclass() call
		try {
			var daoField = service.getClass().getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		setupCacheService(cacheService);
	}

	@Nested
	class EvictTokensOfUserTests {

		@Test
		void evictTokensOfUser_WithTokens_EvictsAndReturnsOne() {
			String token1 = "token-abc-123";
			String token2 = "token-def-456";

			when(dao.getTokensOfId(USER_ID)).thenReturn(Flux.just(token1, token2));

			StepVerifier.create(service.evictTokensOfUser(USER_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evict(anyString(), eq(token1));
			verify(cacheService).evict(anyString(), eq(token2));
		}

		@Test
		void evictTokensOfUser_NoTokens_ReturnsOne() {
			when(dao.getTokensOfId(USER_ID)).thenReturn(Flux.empty());

			StepVerifier.create(service.evictTokensOfUser(USER_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	@Nested
	class DeleteTokensTests {

		@Test
		void deleteTokens_EvictsAndDeletesAllTokens() {
			BigInteger userId = BigInteger.valueOf(10);

			when(dao.getTokensOfId(USER_ID)).thenReturn(Flux.just("token-1", "token-2"));
			when(dao.deleteAllTokens(USER_ID)).thenReturn(Mono.just(2));

			StepVerifier.create(service.deleteTokens(userId))
					.assertNext(result -> assertEquals(2, result))
					.verifyComplete();

			verify(dao).getTokensOfId(USER_ID);
			verify(dao).deleteAllTokens(USER_ID);
		}

		@Test
		void deleteTokens_NoExistingTokens_StillDeletesAll() {
			BigInteger userId = BigInteger.valueOf(10);

			when(dao.getTokensOfId(USER_ID)).thenReturn(Flux.empty());
			when(dao.deleteAllTokens(USER_ID)).thenReturn(Mono.just(0));

			StepVerifier.create(service.deleteTokens(userId))
					.assertNext(result -> assertEquals(0, result))
					.verifyComplete();
		}
	}

	@Nested
	class CreateTests {

		@Test
		void create_CreatesToken() {
			var ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			TokenObject token = TestDataFactory.createTokenObject(null, USER_ID, "new-token-value",
					LocalDateTime.now().plusHours(1));

			TokenObject createdToken = TestDataFactory.createTokenObject(TOKEN_ID, USER_ID,
					"new-token-value", LocalDateTime.now().plusHours(1));

			when(dao.create(any(TokenObject.class))).thenReturn(Mono.just(createdToken));

			StepVerifier.create(service.create(token))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(TOKEN_ID, result.getId());
						assertEquals(USER_ID, result.getUserId());
						assertEquals("new-token-value", result.getToken());
					})
					.verifyComplete();
		}
	}

	@Nested
	class ReadAllFilterTests {

		@Test
		void readAllFilter_ReturnsTokens() {
			TokenObject token1 = TestDataFactory.createTokenObject(TOKEN_ID, USER_ID,
					"token-1", LocalDateTime.now().plusHours(1));
			ULong tokenId2 = ULong.valueOf(501);
			TokenObject token2 = TestDataFactory.createTokenObject(tokenId2, USER_ID,
					"token-2", LocalDateTime.now().plusHours(2));

			when(dao.readAll(any())).thenReturn(Flux.just(token1, token2));

			StepVerifier.create(service.readAllFilter(null).collectList())
					.assertNext(result -> {
						assertEquals(2, result.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	class DeleteByIdTests {

		@Test
		void delete_ById_DeletesToken() {
			when(dao.delete(TOKEN_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(TOKEN_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_ById_NotFound_ReturnsZero() {
			when(dao.delete(TOKEN_ID)).thenReturn(Mono.just(0));

			StepVerifier.create(service.delete(TOKEN_ID))
					.assertNext(result -> assertEquals(0, result))
					.verifyComplete();
		}
	}
}
