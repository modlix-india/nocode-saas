package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dto.rule.base.EntityRule;
import com.fincity.saas.entity.processor.dto.rule.base.RuleExecutionConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for executing rules with configurable execution behavior.
 * This service allows executing a list of rules with a specific execution configuration.
 */
@Service
@Slf4j
public class RuleExecutionService {

    /**
     * Executes a list of rules with the given execution configuration and context.
     *
     * @param rules The list of rules to execute
     * @param config The rule execution configuration
     * @param context The context for rule evaluation
     * @param ruleEvaluator A function that evaluates a rule against the context and returns true if the rule matches
     * @return A flux of rules that matched
     */
    public <T extends EntityRule<T>> Flux<T> executeRules(
            List<T> rules,
            RuleExecutionConfig config,
            Map<String, Object> context,
            Function<T, Mono<Boolean>> ruleEvaluator) {

        if (rules == null || rules.isEmpty()) {
            return Flux.empty();
        }

        // Sort rules by order
        List<T> sortedRules = rules.stream()
                .sorted((r1, r2) -> {
                    Integer order1 = r1.getRuleOrder() != null ? r1.getRuleOrder() : Integer.MAX_VALUE;
                    Integer order2 = r2.getRuleOrder() != null ? r2.getRuleOrder() : Integer.MAX_VALUE;
                    return order1.compareTo(order2);
                })
                .toList();

        return Flux.fromIterable(sortedRules).concatMap(rule -> executeRule(rule, config, context, ruleEvaluator));
    }

    /**
     * Executes a single rule with the given execution configuration and context.
     *
     * @param rule The rule to execute
     * @param config The rule execution configuration
     * @param context The context for rule evaluation
     * @param ruleEvaluator A function that evaluates a rule against the context and returns true if the rule matches
     * @return A mono of the rule if it matched, or an empty mono if it didn't match
     */
    private <T extends EntityRule<T>> Mono<T> executeRule(
            T rule, RuleExecutionConfig config, Map<String, Object> context, Function<T, Mono<Boolean>> ruleEvaluator) {

        return ruleEvaluator.apply(rule).flatMap(matches -> {
            if (matches) {
                log.debug("Rule {} matched", rule.getId());
                return Mono.just(rule);
            } else {
                log.debug("Rule {} did not match", rule.getId());
                return config.isContinueOnNoMatch() ? Mono.empty() : Mono.error(new RuleExecutionStoppedException());
            }
        });
    }

    /**
     * Exception thrown when rule execution is stopped due to a rule not matching
     * and the configuration specifies to stop on no match.
     */
    public static class RuleExecutionStoppedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Example of how to use the RuleExecutionService:
     *
     * <pre>
     * {@code
     * // Create a rule execution configuration
     * RuleExecutionConfig config = RuleExecutionConfig.breakAtFirstMatch();
     *
     * // Create a context for rule evaluation
     * Map<String, Object> context = new HashMap<>();
     * context.put("userId", 123);
     * context.put("productId", 456);
     *
     * // Define a rule evaluator function
     * Function<ProductRule, Mono<Boolean>> ruleEvaluator = rule -> {
     *     // Evaluate the rule against the context
     *     // This is just an example, the actual implementation would depend on your rule structure
     *     return Mono.just(true); // Always match for this example
     * };
     *
     * // Execute the rules
     * Flux<ProductRule> matchedRules = ruleExecutionService.executeRules(
     *     productRules,
     *     config,
     *     context,
     *     ruleEvaluator
     * );
     *
     * // Process the matched rules
     * matchedRules.subscribe(
     *     rule -> log.info("Rule matched: {}", rule.getId()),
     *     error -> log.error("Error executing rules", error),
     *     () -> log.info("Rule execution completed")
     * );
     * }
     * </pre>
     */
}
