package com.fincity.saas.message.service.bridge;

import java.io.Serial;

/**
 * A link was requested for a number that already has a session placed on an instance.
 *
 * <p>Its own type so the caller can answer CONFLICT with a sentence a person can act on. The
 * database already refuses this, through the unique key on the generated linked-number column, but
 * arriving there means the failure surfaces as an integrity violation whose message is the entire
 * SQL statement. That text was being stored as the session reason and rendered to the customer.
 *
 * <p>Not a defect on the customer's part. Clicking Link twice, or linking again after a pairing
 * attempt was abandoned, is ordinary. The answer is to tell them the number is already being linked
 * and let them unlink it first.
 */
public class BridgeNumberAlreadyLinkedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 7132894004416255310L;

    private final String phone;
    private final String state;

    public BridgeNumberAlreadyLinkedException(String phone, String state) {
        super("The number " + phone + " already has a session"
                + (state == null ? "" : " in state " + state)
                + ". Unlink it before linking it again.");
        this.phone = phone;
        this.state = state;
    }

    public String getPhone() {
        return this.phone;
    }

    public String getState() {
        return this.state;
    }
}
