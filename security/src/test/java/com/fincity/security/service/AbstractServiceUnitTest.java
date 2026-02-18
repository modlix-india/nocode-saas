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
		securityContextMock = Mockito.mockStatic(SecurityContextUtil.class);
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
		lenient().when(messageResourceService.throwMessage(any(Function.class), anyString()))
				.thenAnswer(invocation -> {
					Function<String, GenericException> fn = invocation.getArgument(0);
					String messageId = invocation.getArgument(1);
					return Mono.error(fn.apply(messageId));
				});

		lenient().when(messageResourceService.throwMessage(any(Function.class), anyString(), any()))
				.thenAnswer(invocation -> {
					Function<String, GenericException> fn = invocation.getArgument(0);
					String messageId = invocation.getArgument(1);
					return Mono.error(fn.apply(messageId));
				});

		lenient().when(messageResourceService.getMessage(anyString()))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0).toString()));

		lenient().when(messageResourceService.getMessage(anyString(), any()))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0).toString()));

		lenient().when(messageResourceService.getMessage(anyString(), any(Object[].class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0).toString()));
	}

	@SuppressWarnings("unchecked")
	protected void setupCacheService(CacheService cacheService) {
		lenient().when(cacheService.cacheValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});

		lenient().when(cacheService.evict(anyString(), any(Object[].class)))
				.thenReturn(Mono.just(true));

		lenient().when(cacheService.evict(anyString(), anyString()))
				.thenReturn(Mono.just(true));

		lenient().when(cacheService.evictAll(anyString()))
				.thenReturn(Mono.just(true));

		lenient().when(cacheService.put(anyString(), any(), any()))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(1)));

		lenient().when(cacheService.get(anyString(), any(Object[].class)))
				.thenReturn(Mono.empty());
	}

	protected void setupSoxLogService(SoxLogService soxLogService) {
		lenient().doNothing().when(soxLogService)
				.createLog(any(), any(), any(), anyString());
	}
}
