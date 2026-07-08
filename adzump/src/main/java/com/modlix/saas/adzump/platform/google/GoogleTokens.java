package com.modlix.saas.adzump.platform.google;

import org.springframework.http.HttpStatus;

import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * Validation + id-normalisation for the Google context that rides on the SPI {@link Token} (J2b §5.1:
 * Google needs more than a bare token — an OAuth access token, the {@code login-customer-id} (MCC),
 * and the operating {@code customer-id}). These checks are the fail-fast seam the design calls for:
 * a missing MCC / customer context throws a clear {@link GenericException} <b>before</b> any call
 * reaches the {@link GoogleAdsClientFacade}, so the failure is unit-testable offline and never turns
 * into an opaque gRPC error live.
 */
final class GoogleTokens {

    private GoogleTokens() {
    }

    /** Digits only — Google customer / MCC ids are numeric; the UI/connection may carry dashes. */
    static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** The trailing id segment of a resource name ({@code customers/123/campaigns/456} -> {@code 456}). */
    static String idFromResourceName(String resourceName) {
        if (resourceName == null || resourceName.isBlank())
            return null;
        int slash = resourceName.lastIndexOf('/');
        return slash < 0 ? resourceName : resourceName.substring(slash + 1);
    }

    static String requireAccessToken(Token t, AdzumpMessageResourceService msg) {
        if (t == null || t.accessToken() == null || t.accessToken().isBlank())
            return msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "Google OAuth access token");
        return t.accessToken();
    }

    /** The MCC / login-customer-id (numeric) required on every Google call; fails fast when absent. */
    static long requireLoginCustomerId(Token t, AdzumpMessageResourceService msg) {
        String d = digits(t == null ? null : t.loginCustomerId());
        if (d.isEmpty())
            msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "Google login-customer-id (MCC)");
        return Long.parseLong(d);
    }

    /** The operating customer-id (numeric); fails fast when absent. */
    static long requireCustomerId(Token t, AdzumpMessageResourceService msg) {
        String d = digits(t == null ? null : t.accountId());
        if (d.isEmpty())
            msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "Google customer-id (account)");
        return Long.parseLong(d);
    }

    /** The operating customer-id as the digits-only string the service convenience overloads expect. */
    static String requireCustomerIdString(Token t, AdzumpMessageResourceService msg) {
        return Long.toString(requireCustomerId(t, msg));
    }
}
