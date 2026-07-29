package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleBundleType;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A purchasable token bundle under a billing config. FIXED = a fixed
 * tokens+price tier; CUSTOM = the buyer enters a token quantity priced at
 * {@code pricePerToken} (bounded by min/max).
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppBillingBundle extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong billingConfigId;
    private String label;
    private SecurityAppBillingBundleBundleType bundleType;
    private BigDecimal tokens;
    private BigDecimal price;
    private BigDecimal pricePerToken;
    private BigDecimal minTokens;
    private BigDecimal maxTokens;
    private String currency;
    private SecurityAppBillingBundleStatus status;
    private Integer displayOrder;
}
