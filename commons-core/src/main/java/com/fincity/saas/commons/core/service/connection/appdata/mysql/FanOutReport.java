package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;
import java.util.Map;

/**
 * The result of a publish across every tenant of an app.
 *
 * Progress, not success. One definition is shared by up to 71 tenant schemas, so
 * partial completion is a normal state rather than an error, and the caller needs to
 * know which tenants are clean and which are blocked rather than a boolean.
 *
 * @param byTenant what happened on each tenant that had work to do
 * @param blocked  tenants that did not finish, whether stopped before they started by
 *                 the pre-flight or part way through
 * @param noOp     tenants already at the right shape, which for an overridable
 *                 definition is a normal outcome rather than a sign nothing happened:
 *                 a descendant that pinned its own type for a field is genuinely
 *                 unaffected by the base changing it
 * @param resumed  tenants where this run continued a migration that had stopped part
 *                 way on an earlier attempt
 */
public record FanOutReport(
        Map<String, MigrationOutcome> byTenant, List<String> blocked, List<String> noOp, List<String> resumed) {

    public FanOutReport(Map<String, MigrationOutcome> byTenant, List<String> blocked, List<String> noOp) {
        this(byTenant, blocked, noOp, List.of());
    }

    public int total() {
        return this.byTenant.size() + this.noOp.size();
    }

    public int applied() {
        return (int) this.byTenant.values().stream().filter(MigrationOutcome::isSuccess).count();
    }

    public boolean allClean() {
        return this.blocked.isEmpty();
    }

    public static FanOutReport empty() {
        return new FanOutReport(Map.of(), List.of(), List.of(), List.of());
    }

    /**
     * Two halves of one publish, usually the live and draft surfaces.
     *
     * They are run separately because each tenant's journal records which surface it
     * was, and reported together because an author published once and should be told
     * about it once.
     */
    public static FanOutReport merge(FanOutReport a, FanOutReport b) {

        Map<String, MigrationOutcome> byTenant = new java.util.LinkedHashMap<>(a.byTenant);
        byTenant.putAll(b.byTenant);

        return new FanOutReport(
                byTenant, concat(a.blocked, b.blocked), concat(a.noOp, b.noOp), concat(a.resumed, b.resumed));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** One line a human can act on, which is the point of reporting per tenant. */
    public String summary() {
        if (this.total() == 0) return "no tenants hold this storage";

        String resumedNote = this.resumed.isEmpty() ? "" : ", " + this.resumed.size() + " resumed";

        if (this.blocked.isEmpty())
            return this.applied() + " migrated, " + this.noOp.size() + " already current" + resumedNote
                    + ", none blocked";

        return this.applied() + " migrated, " + this.noOp.size() + " already current" + resumedNote + ", "
                + this.blocked.size() + " blocked: " + this.blocked;
    }
}
