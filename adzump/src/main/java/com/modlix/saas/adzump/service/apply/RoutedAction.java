package com.modlix.saas.adzump.service.apply;

import com.modlix.saas.adzump.service.optimize.Action;

/**
 * One {@link Action} paired with the {@link AutonomyRouter}'s {@link ApplyRoute} decision and the
 * machine-readable {@code reason} for it (J13 §5.1). The {@link ApplyPlan} is a list of these; the
 * {@link ActionApplier} consumes each — applying the {@link ApplyRoute#APPLY} ones through the one
 * mutation spine (after the {@link GuardrailEngine}) and auditing the rest.
 */
public record RoutedAction(Action action, ApplyRoute route, String reason) {

    public static RoutedAction apply(Action action, String reason) {
        return new RoutedAction(action, ApplyRoute.APPLY, reason);
    }

    public static RoutedAction queue(Action action, String reason) {
        return new RoutedAction(action, ApplyRoute.QUEUE, reason);
    }

    public static RoutedAction suppress(Action action, String reason) {
        return new RoutedAction(action, ApplyRoute.SUPPRESS, reason);
    }
}
