package com.fincity.saas.entity.processor.enums;

import org.jooq.EnumType;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum PartnerVerificationStatus implements EnumType {
    INVITATION_SENT("INVITATION_SENT", "Invitation Sent"),
    REQUEST_REVOKED("REQUEST_REVOKED", "Request Revoked"),
    APPROVAL_PENDING("APPROVAL_PENDING", "Approval Pending"),
    REQUEST_CORRECTION("REQUEST_CORRECTION", "Request Correction"),
    REJECTED("REJECTED", "Rejected"),
    VERIFIED("VERIFIED", "Verified");

    private final String literal;
    private final String value;

    PartnerVerificationStatus(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static PartnerVerificationStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(PartnerVerificationStatus.class, literal);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }
}
