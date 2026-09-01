package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

/**
 * The core publish routes are actually mapped.
 *
 * Worth its own test because of how they are declared: `@GetMapping` and
 * `@PostMapping` live on `AbstractPublishController`, which carries no
 * `@RestController` of its own, and only the subclass has `@RequestMapping`.
 * Spring does inherit annotated handler methods that way, but if it ever stopped,
 * or if someone gave the abstract class its own `@RequestMapping`, both endpoints
 * would 404 while every service-level test in this suite stayed green: those call
 * PublishService directly and never touch the HTTP layer.
 */
@DisplayName("UI publish routes")
class PublishRouteRegistrationTest extends AbstractIntegrationTest {

    // Qualified: actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and an unqualified injection is ambiguous.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private Set<String> mappedPatterns() {
        return this.handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternsCondition() != null)
                .flatMap(info -> info.getPatternsCondition().getPatterns().stream())
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @Timeout(60)
    @DisplayName("pending and publishAll are both reachable under api/ui/publish")
    void routesAreMapped() {

        Set<String> patterns = mappedPatterns();

        assertTrue(patterns.contains("/api/ui/publish/app/{appCode}/pending"),
                "GET pending is not mapped, so the ui pending list 404s: " + sample(patterns));
        assertTrue(patterns.contains("/api/ui/publish/app/{appCode}"),
                "POST publishAll is not mapped, so ui drafts can never be shipped: " + sample(patterns));
    }

    private static String sample(Set<String> patterns) {
        return patterns.stream().filter(p -> p.contains("publish")).sorted().toList().toString();
    }
}
