package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;

/**
 * The draft flag is read from the Reactor Context by every draft-aware code path,
 * so if it does not survive the wrappers those paths all silently fall back to
 * live. This isolates that one question from anything that depends on it.
 */
@DisplayName("Draft flag propagation")
class DraftContextPropagationTest extends AbstractIntegrationTest {

    @Test
    @Timeout(30)
    @DisplayName("defaults to false with no context written")
    void defaultsToFalse() {
        assertEquals(Boolean.FALSE, LogUtil.isDraft().block());
    }

    @Test
    @Timeout(30)
    @DisplayName("is visible after onDraftSurface")
    void visibleOnDraftSurface() {
        assertEquals(Boolean.TRUE, this.onDraftSurface(LogUtil.isDraft()).block());
    }

    @Test
    @Timeout(30)
    @DisplayName("survives a security context write layered underneath it")
    void survivesSecurityContextWrite() {

        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));

        Mono<Boolean> withAuth = LogUtil.isDraft()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));

        assertEquals(Boolean.TRUE, this.onDraftSurface(withAuth).block(),
                "the security context write must not shadow the draft flag");
    }

    @Test
    @Timeout(30)
    @DisplayName("survives being read from deep inside a flatMap chain")
    void survivesNesting() {

        Mono<Boolean> nested = Mono.just("start")
                .flatMap(s -> Mono.just(s).map(x -> x + "-1"))
                .flatMap(s -> LogUtil.isDraft());

        assertEquals(Boolean.TRUE, this.onDraftSurface(nested).block());
    }
}
