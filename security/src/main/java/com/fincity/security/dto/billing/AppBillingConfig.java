package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigDefaultPaymentGateway;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Per-app billing configuration. Replaces the old Plan as the per-app config
 * home: default gateway, seat token burn rate, monthly free grant and the
 * enforcement (turn-on) flag.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppBillingConfig extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong appId;
    private ULong clientId;
    private SecurityAppBillingConfigDefaultPaymentGateway defaultPaymentGateway;
    private boolean seatBillingEnabled;
    private BigDecimal seatTokensPerMonth;
    private BigDecimal monthlyFreeTokens;
    private boolean enforced;
    private String suspendAppCode;
    private String suspendClientCode;
    private SecurityAppBillingConfigStatus status;
}
