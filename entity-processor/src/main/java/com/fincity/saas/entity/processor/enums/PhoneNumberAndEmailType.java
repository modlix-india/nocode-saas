package com.fincity.saas.entity.processor.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import java.util.Objects;
import lombok.Getter;
import org.jooq.EnumType;
import org.springframework.http.HttpStatus;

@Getter
public enum PhoneNumberAndEmailType implements EnumType {
    PHONE_NUMBER_ONLY("PHONE_NUMBER_ONLY", "Phone Number Only"),
    EMAIL_ONLY("EMAIL_ONLY", "Email Only"),
    PHONE_NUMBER_AND_EMAIL("PHONE_NUMBER_AND_EMAIL", "Phone Number And Email"),
    PHONE_NUMBER_OR_EMAIL("PHONE_NUMBER_OR_EMAIL", "Phone Number Or Email");

    private final String literal;
    private final String value;

    PhoneNumberAndEmailType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static PhoneNumberAndEmailType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(PhoneNumberAndEmailType.class, literal);
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

    public AbstractCondition getTicketCondition(PhoneNumber phoneNumber, Email email) {
        return getCondition(Ticket.Fields.dialCode, Ticket.Fields.phoneNumber, Ticket.Fields.email, phoneNumber, email);
    }

    public AbstractCondition getCondition(
            String dialCodeField, String phoneNumberField, String emailField, PhoneNumber phoneNumber, Email email) {

        AbstractCondition phoneCondition = this.buildPhoneNumberCondition(dialCodeField, phoneNumberField, phoneNumber);
        AbstractCondition emailCondition = this.buildEmailCondition(emailField, email);

        return switch (this) {
            case PHONE_NUMBER_ONLY -> {
                this.validatePhoneCondition(phoneCondition);
                yield phoneCondition;
            }
            case EMAIL_ONLY -> {
                this.validateEmailCondition(emailCondition);
                yield emailCondition;
            }
            case PHONE_NUMBER_AND_EMAIL -> {
                this.validatePhoneCondition(phoneCondition);
                this.validateEmailCondition(emailCondition);
                yield ComplexCondition.and(phoneCondition, emailCondition);
            }
            case PHONE_NUMBER_OR_EMAIL -> {
                if (phoneCondition == null && emailCondition == null)
                    throw new IllegalArgumentException(
                            "At least one of phone number or email is required to continue.");

                if (phoneCondition == null) yield emailCondition;
                if (emailCondition == null) yield phoneCondition;
                yield ComplexCondition.or(phoneCondition, emailCondition);
            }
        };
    }

    private AbstractCondition buildPhoneNumberCondition(
            String dialCodeField, String phoneNumberField, PhoneNumber phoneNumber) {

        if (phoneNumber == null || phoneNumber.getCountryCode() == null || phoneNumber.getNumber() == null) {
            return null;
        }

        return ComplexCondition.and(
                FilterCondition.make(
                        Objects.requireNonNullElse(dialCodeField, PhoneNumber.Fields.countryCode),
                        phoneNumber.getCountryCode()),
                FilterCondition.make(
                        Objects.requireNonNullElse(phoneNumberField, PhoneNumber.Fields.number),
                        phoneNumber.getNumber()));
    }

    private AbstractCondition buildEmailCondition(String emailField, Email email) {
        if (email == null || email.getAddress() == null) return null;

        return FilterCondition.make(Objects.requireNonNullElse(emailField, Email.Fields.address), email.getAddress());
    }

    /*
     * These two describe the REQUEST, not a fault in this service, so they must carry a 4xx.
     *
     * As a bare IllegalArgumentException they did not: ControllerAdvice has no case for it, so it
     * fell through to INTERNAL_SERVER_ERROR. On production, 2026-09-29, that was every Facebook
     * lead arriving without a usable phone number - 40 in a day, each one producing TWO 500s and
     * a stack trace, because the inbound webhook at /entry/social/facebook calls
     * /open/tickets/req/campaigns over HTTP and re-raises whatever it gets back.
     *
     * A 500 on a webhook is not a cosmetic problem: Meta retries 5xx and does not retry 4xx, so
     * a lead we can never accept was being redelivered instead of rejected.
     */
    private void validatePhoneCondition(AbstractCondition phoneCondition) {
        if (phoneCondition == null)
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    "Valid phone number (with country code and number) is required to continue.");
    }

    private void validateEmailCondition(AbstractCondition emailCondition) {
        if (emailCondition == null)
            throw new GenericException(HttpStatus.BAD_REQUEST, "Valid email address is required to continue.");
    }
}
