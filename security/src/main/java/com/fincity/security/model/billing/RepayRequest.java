package com.fincity.security.model.billing;

import org.jooq.types.ULong;

/** Repay request body: the PENDING/FAILED invoice the caller wants to start a fresh order for. */
public record RepayRequest(ULong invoiceId) {
}
