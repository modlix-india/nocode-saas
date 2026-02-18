package com.fincity.security.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Mono;

@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractServiceUnitTest {

	protected MockedStatic<SecurityContextUtil> securityContextMock;

	@BeforeEach
	void setUpSecurityContext() {
		securityContextMock = Mockito.mockStatic(SecurityContextUtil.class, invocation -> {
			// Delegate hasAuthority(String, Collection/List) to real implementation
			// since it's a pure utility method with no external dependencies
			if ("hasAuthority".equals(invocation.getMethod().getName())
					&& invocation.getArguments().length >= 2) {
				return invocation.callRealMethod();
			}
			return Mockito.RETURNS_DEFAULTS.answer(invocation);
		});
	}

	@AfterEach
	void tearDownSecurityContext() {
		if (securityContextMock != null) {
			securityContextMock.close();
		}
	}

	protected void setupSecurityContext(ContextAuthentication ca) {
		securityContextMock.when(SecurityContextUtil::getUsersContextAuthentication)
				.thenReturn(Mono.just(ca));
		securityContextMock.when(SecurityContextUtil::getUsersContextUser)
				.thenReturn(Mono.just(ca.getUser()));
	}

	protected void setupEmptySecurityContext() {
		securityContextMock.when(SecurityContextUtil::getUsersContextAuthentication)
				.thenReturn(Mono.empty());
		securityContextMock.when(SecurityContextUtil::getUsersContextUser)
				.thenReturn(Mono.empty());
	}

	@SuppressWarnings("unchecked")
	protected void setupMessageResourceService(SecurityMessageResourceService messageResourceService) {
		Answer<Object> throwMessageAnswer = invocation -> {
			Function<String, GenericException> fn = invocation.getArgument(0);
			if (fn == null) return Mono.empty();
			String messageId = invocation.getArgument(1);
			return Mono.error(fn.apply(messageId));
		};

		// Use doAnswer().when() pattern to avoid triggering existing stubs during setup
		lenient().doAnswer(throwMessageAnswer).when(messageResourceService)
				.throwMessage(any(Function.class), anyString());
		lenient().doAnswer(throwMessageAnswer).when(messageResourceService)
				.throwMessage(any(Function.class), anyString(), any());
		lenient().doAnswer(throwMessageAnswer).when(messageResourceService)
				.throwMessage(any(Function.class), anyString(), any(), any());
		lenient().doAnswer(throwMessageAnswer).when(messageResourceService)
				.throwMessage(any(Function.class), anyString(), any(), any(), any());

		Answer<Object> getMessageAnswer = invocation -> Mono.just(invocation.getArgument(0).toString());

		lenient().doAnswer(getMessageAnswer).when(messageResourceService).getMessage(anyString());
		lenient().doAnswer(getMessageAnswer).when(messageResourceService).getMessage(anyString(), any());
		lenient().doAnswer(getMessageAnswer).when(messageResourceService)
				.getMessage(anyString(), any(Object[].class));
	}

	@SuppressWarnings("unchecked")
	protected void setupCacheService(CacheService cacheService) {
		Answer<Object> cacheAnswer = invocation -> {
			java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
			return supplier != null ? supplier.get() : Mono.empty();
		};

		// Use doAnswer().when() pattern to avoid triggering existing stubs during setup
		// cacheValueOrGet with varying number of key arguments (varargs)
		lenient().doAnswer(cacheAnswer).when(cacheService).cacheValueOrGet(anyString(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService).cacheValueOrGet(anyString(), any(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService)
				.cacheValueOrGet(anyString(), any(), any(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService)
				.cacheValueOrGet(anyString(), any(), any(), any(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService)
				.cacheValueOrGet(anyString(), any(), any(), any(), any(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService)
				.cacheValueOrGet(anyString(), any(), any(), any(), any(), any(), any(), any());

		// cacheEmptyValueOrGet with varying number of key arguments
		lenient().doAnswer(cacheAnswer).when(cacheService).cacheEmptyValueOrGet(anyString(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService).cacheEmptyValueOrGet(anyString(), any(), any(), any());
		lenient().doAnswer(cacheAnswer).when(cacheService)
				.cacheEmptyValueOrGet(anyString(), any(), any(), any(), any());

		lenient().doReturn(Mono.just(true)).when(cacheService).evict(anyString(), any(Object[].class));
		lenient().doReturn(Mono.just(true)).when(cacheService).evict(anyString(), anyString());
		lenient().doReturn(Mono.just(true)).when(cacheService).evictAll(anyString());

		lenient().doAnswer(invocation -> Mono.just(invocation.getArgument(1)))
				.when(cacheService).put(anyString(), any(), any());

		lenient().doReturn(Mono.empty()).when(cacheService).get(anyString(), any(Object[].class));

		// evictFunction returns Function<T, Mono<T>> that passes through the value
		@SuppressWarnings("rawtypes")
		Answer<Function> evictFnAnswer = invocation -> v -> Mono.just(v);
		lenient().doAnswer(evictFnAnswer).when(cacheService).evictFunction(anyString(), any(Object[].class));
		lenient().doAnswer(evictFnAnswer).when(cacheService).evictAllFunction(anyString());
		lenient().doAnswer(evictFnAnswer).when(cacheService).evictFunctionWithKeyFunction(anyString(), any());
	}

	protected void setupSoxLogService(SoxLogService soxLogService) {
		lenient().doNothing().when(soxLogService)
				.createLog(any(), any(), any(), anyString());
	}
}
