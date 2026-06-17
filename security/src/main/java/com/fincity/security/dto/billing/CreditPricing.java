package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityCreditPricingCostBasisType;
import com.fincity.security.jooq.enums.SecurityCreditPricingStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Converts a real vendor cost into tokens (credits) with a margin multiplier.
 * Used to price LLM tokens, SMS segments, etc. Formula:
 * credits = quantity * vendorUnitCost * markupMultiplier * creditsPerCurrencyUnit.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CreditPricing extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private SecurityCreditPricingCostBasisType costBasisType;
    private String vendor;
    private String modelOrSku;
    private String unit;
    private BigDecimal vendorUnitCost;
    private String currency;
    private BigDecimal markupMultiplier;
    private BigDecimal creditsPerCurrencyUnit;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private SecurityCreditPricingStatus status;
}
