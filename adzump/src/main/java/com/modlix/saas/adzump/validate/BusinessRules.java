package com.modlix.saas.adzump.validate;

import static com.modlix.saas.adzump.validate.ValidationSupport.isBlank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.BudgetSplit;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.ScheduleConfig;

/**
 * Layer 5 — <b>business</b>. Budget sanity (exactly one of daily / total budget; a provided split sums to
 * 100 over targeted platforms), schedule sanity (end after start), and the <b>studied-product guard</b>:
 * no launch without a product profile (CONTRACT §6.2). Most findings are {@code ERROR}; thin budget is a
 * {@code WARNING} (surfaces to the agent + eval but never blocks) so the WARNING-vs-ERROR policy is
 * exercised here.
 *
 * <p>TODO(J9 studied-flag): the product guard currently checks {@code productId} presence; once A2/J9
 * expose a "studied" flag on the product profile it must additionally require the study to have
 * completed. TODO: budget caps ("within caps") activate once J7 threads effective policy into
 * {@code ctx.effectiveConfig()}.
 */
public final class BusinessRules {

    public static final String BUSINESS_NO_PRODUCT_PROFILE = "BUSINESS_NO_PRODUCT_PROFILE";
    public static final String BUSINESS_BUDGET_XOR = "BUSINESS_BUDGET_XOR";
    public static final String BUSINESS_BUDGET_SPLIT = "BUSINESS_BUDGET_SPLIT";
    public static final String BUSINESS_SCHEDULE_ORDER = "BUSINESS_SCHEDULE_ORDER";
    public static final String BUSINESS_THIN_BUDGET = "BUSINESS_THIN_BUDGET";

    private static final BigDecimal THIN_DAILY_FLOOR = BigDecimal.ONE;
    private static final double SPLIT_TOTAL = 100.0;
    private static final double SPLIT_EPSILON = 0.01;

    private BusinessRules() {
    }

    public static List<ValidationIssue> check(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();

        // Studied-product guard (hard stop): no launch without a product profile.
        if (plan == null || isBlank(plan.getProductId()))
            issues.add(ValidationIssue.error(BUSINESS_NO_PRODUCT_PROFILE, "/productId", "productId",
                    "no studied product profile; a plan cannot launch without one"));

        CampaignPlanBody body = plan == null ? null : plan.getBody();
        if (body == null)
            return issues;

        checkBudget(issues, plan, body.getBudget());
        checkSchedule(issues, body.getSchedule());

        return issues;
    }

    private static void checkBudget(List<ValidationIssue> issues, CampaignPlan plan, BudgetPlan budget) {

        if (budget == null)
            return; // structural layer flags the absent budget

        boolean daily = hasAmount(budget.getDailyBudget());
        boolean total = hasAmount(budget.getTotalBudget());

        // XOR: exactly one of daily / total must be set (both set OR neither set is an error).
        if (daily == total)
            issues.add(ValidationIssue.error(BUSINESS_BUDGET_XOR, "/body/budget", "budget",
                    "exactly one of dailyBudget or totalBudget must be set"));

        if (daily && budget.getDailyBudget().getAmount().compareTo(THIN_DAILY_FLOOR) < 0)
            issues.add(ValidationIssue.warning(BUSINESS_THIN_BUDGET, "/body/budget/dailyBudget", "dailyBudget",
                    "daily budget is below a sane delivery floor"));

        checkSplit(issues, plan, budget.getSplit());
    }

    private static void checkSplit(List<ValidationIssue> issues, CampaignPlan plan, List<BudgetSplit> split) {

        if (split == null || split.isEmpty())
            return;

        double sum = 0.0;
        for (BudgetSplit s : split)
            if (s != null && s.getPercent() != null)
                sum += s.getPercent();

        if (Math.abs(sum - SPLIT_TOTAL) > SPLIT_EPSILON)
            issues.add(ValidationIssue.error(BUSINESS_BUDGET_SPLIT, "/body/budget/split", "split",
                    "budget split must sum to 100 over targeted platforms (was " + sum + ")"));

        Set<Platform> targeted = (plan.getCampaignTypes() == null)
                ? Set.of()
                : plan.getCampaignTypes().keySet();
        for (int i = 0; i < split.size(); i++) {
            BudgetSplit s = split.get(i);
            if (s != null && s.getPlatform() != null && !targeted.contains(s.getPlatform()))
                issues.add(ValidationIssue.error(BUSINESS_BUDGET_SPLIT, "/body/budget/split/" + i + "/platform",
                        "platform", "budget split references untargeted platform " + s.getPlatform()));
        }
    }

    private static void checkSchedule(List<ValidationIssue> issues, ScheduleConfig schedule) {

        if (schedule == null || schedule.getStartAt() == null || schedule.getEndAt() == null)
            return;

        if (!schedule.getEndAt().isAfter(schedule.getStartAt()))
            issues.add(ValidationIssue.error(BUSINESS_SCHEDULE_ORDER, "/body/schedule", "schedule",
                    "schedule end must be after start"));
    }

    private static boolean hasAmount(Money m) {
        return m != null && m.getAmount() != null;
    }
}
