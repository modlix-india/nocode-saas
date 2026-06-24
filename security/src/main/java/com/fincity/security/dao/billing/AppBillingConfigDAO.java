package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_APP;
import static com.fincity.security.jooq.Tables.SECURITY_APP_BILLING_CONFIG;
import static com.fincity.security.jooq.Tables.SECURITY_CLIENT;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigStatus;
import com.fincity.security.jooq.tables.records.SecurityAppBillingConfigRecord;
import com.fincity.security.model.billing.BillingActionKeys;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AppBillingConfigDAO
        extends AbstractUpdatableDAO<SecurityAppBillingConfigRecord, ULong, AppBillingConfig> {

    protected AppBillingConfigDAO() {
        super(AppBillingConfig.class, SECURITY_APP_BILLING_CONFIG, SECURITY_APP_BILLING_CONFIG.ID);
    }

    /** All non-deleted configs for an app (one per configurator client). */
    public Mono<List<AppBillingConfig>> findByApp(ULong appId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_CONFIG)
                .where(SECURITY_APP_BILLING_CONFIG.APP_ID.eq(appId))
                .and(SECURITY_APP_BILLING_CONFIG.STATUS.ne(SecurityAppBillingConfigStatus.DELETED)))
                .map(r -> r.into(AppBillingConfig.class))
                .collectList();
    }

    public Mono<AppBillingConfig> findByAppAndClient(ULong appId, ULong clientId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_CONFIG)
                .where(SECURITY_APP_BILLING_CONFIG.APP_ID.eq(appId))
                .and(SECURITY_APP_BILLING_CONFIG.CLIENT_ID.eq(clientId))
                .and(SECURITY_APP_BILLING_CONFIG.STATUS.ne(SecurityAppBillingConfigStatus.DELETED)))
                .map(r -> r.into(AppBillingConfig.class));
    }

    /** Resolve a config by app code + configurator client code (joins app + client). */
    public Mono<AppBillingConfig> findByAppCodeAndClientCode(String appCode, String clientCode) {
        return Mono.from(this.dslContext.select(SECURITY_APP_BILLING_CONFIG.fields())
                .from(SECURITY_APP_BILLING_CONFIG)
                .join(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_APP_BILLING_CONFIG.APP_ID))
                .join(SECURITY_CLIENT).on(SECURITY_CLIENT.ID.eq(SECURITY_APP_BILLING_CONFIG.CLIENT_ID))
                .where(SECURITY_APP.APP_CODE.eq(appCode))
                .and(SECURITY_CLIENT.CODE.eq(clientCode))
                .and(SECURITY_APP_BILLING_CONFIG.STATUS.eq(SecurityAppBillingConfigStatus.ACTIVE)))
                .map(r -> r.into(AppBillingConfig.class));
    }

    /** Active configs that carry a non-zero rate for the given action key. */
    public Flux<AppBillingConfig> findConfigsByActionRate(String actionKey) {
        Field<BigDecimal> rate = rateField(actionKey);
        if (rate == null)
            return Flux.empty();
        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_CONFIG)
                .where(SECURITY_APP_BILLING_CONFIG.STATUS.eq(SecurityAppBillingConfigStatus.ACTIVE))
                .and(rate.gt(BigDecimal.ZERO)))
                .map(r -> r.into(AppBillingConfig.class));
    }

    public static Field<BigDecimal> rateField(String actionKey) {
        return switch (actionKey) {
            case BillingActionKeys.APP_RENT -> SECURITY_APP_BILLING_CONFIG.APP_RENT_PER_MONTH;
            case BillingActionKeys.SITE_RENT -> SECURITY_APP_BILLING_CONFIG.SITE_RENT_PER_MONTH;
            case BillingActionKeys.USER -> SECURITY_APP_BILLING_CONFIG.USER_TOKENS_PER_MONTH;
            case BillingActionKeys.STORAGE_ROWS -> SECURITY_APP_BILLING_CONFIG.STORAGE_ROW_TOKENS_PER_MONTH;
            case BillingActionKeys.DEALS -> SECURITY_APP_BILLING_CONFIG.DEAL_TOKENS_PER_MONTH;
            case BillingActionKeys.FILES_GB -> SECURITY_APP_BILLING_CONFIG.FILES_TOKENS_PER_MONTH;
            case BillingActionKeys.AI_LLM_TOKENS -> SECURITY_APP_BILLING_CONFIG.AI_TOKENS_PER_MILLION;
            default -> null;
        };
    }
}
