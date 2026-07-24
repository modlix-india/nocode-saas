package com.fincity.security.dto.billing;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Buyer billing profile (customer-of-record details) per (client, app). Collected
 * on the order-summary screen, reused/pre-filled on later purchases, and
 * snapshotted onto each invoice. The seller-of-record details live on
 * {@link AppBillingConfig}; this is the buyer side.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BillingProfile extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong appId;
    private String legalName;
    private String gstin;
    private String addressLine;
    private String city;
    private String state;
    private String country;
    private String postalCode;
}
