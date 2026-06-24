package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigPaymentGateway;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Per-(configurator client C, app) token-billing config. Holds the per-action
 * rates and free allowances, the GST percentage and gateway config for bundle
 * purchases, the seller-of-record details for invoices, the low-balance warning
 * threshold, and the suspend app/client served when a wallet under it is
 * suspended.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppBillingConfig extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong appId;

    private BigDecimal appRentPerMonth;
    private BigDecimal siteRentPerMonth;
    private BigDecimal filesTokensPerMonth;
    private BigDecimal storageRowTokensPerMonth;
    private BigDecimal dealTokensPerMonth;
    private BigDecimal userTokensPerMonth;
    private BigDecimal aiTokensPerMillion;

    private BigDecimal freeApps;
    private BigDecimal freeSites;
    private BigDecimal freeFilesGb;
    private BigDecimal freeStorageRows;
    private BigDecimal freeDeals;
    private BigDecimal freeUsers;
    private BigDecimal freeAiTokensPerMonth;

    private BigDecimal gstPercentage;
    private SecurityAppBillingConfigPaymentGateway paymentGateway;
    private Map<String, Object> paymentGatewayConfig;

    private String sellerLegalName;
    private String sellerGstin;
    private String sellerAddress;

    private BigDecimal lowBalanceThreshold;
    private String suspendAppCode;
    private String suspendClientCode;

    private SecurityAppBillingConfigStatus status;
}
