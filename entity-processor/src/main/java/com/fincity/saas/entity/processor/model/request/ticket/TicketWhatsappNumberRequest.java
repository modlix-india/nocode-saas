package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Records the number a deal is to be messaged on, when it is not the number it is called on.
 *
 * <p>Its own route rather than a field on the general ticket update, because that update replaces
 * whatever it is given and a client that does not know about this field would clear it on every
 * unrelated save. It also matches how the number is actually learned: an agent rings a lead whose
 * messages are going nowhere, is told a different number, and changes that one thing.
 *
 * <p>A null or blank {@link #whatsappNumber} clears it, putting the deal back on its phone number.
 * That is a real operation and not a no-op: the correction is sometimes itself wrong.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TicketWhatsappNumberRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5471902264871330941L;

    private PhoneNumber whatsappNumber;

    /** Why it changed. Worth recording: this number is hearsay from a phone call. */
    private String comment;
}
