package com.modlix.saas.worker.enums;

import org.jooq.EnumType;

public enum TaskJobType implements EnumType {
    SSL_RENEWAL("SSL_RENEWAL"),
    TOKEN_CLEANUP("TOKEN_CLEANUP"),
    PARTNER_DENORM_DELTA("PARTNER_DENORM_DELTA"),
    PARTNER_DENORM_FULL("PARTNER_DENORM_FULL"),
    CAMPAIGN_METRICS_SYNC("CAMPAIGN_METRICS_SYNC"),
    CAMPAIGN_DISCOVERY_SYNC("CAMPAIGN_DISCOVERY_SYNC"),
    CONVERSIONS_API_DISPATCH("CONVERSIONS_API_DISPATCH"),
    SECURITY_METERING("SECURITY_METERING"),
    CORE_METERING("CORE_METERING"),
    ENTITY_PROCESSOR_METERING("ENTITY_PROCESSOR_METERING"),
    FILES_METERING("FILES_METERING"),
    BILLING_RECONCILE("BILLING_RECONCILE");

    private final String literal;

    TaskJobType(String literal) {
        this.literal = literal;
    }

    public static TaskJobType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TaskJobType.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }
}
