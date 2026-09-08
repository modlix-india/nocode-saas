package com.fincity.saas.entity.processor.model.request.product;

import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The complete set of products that should send from one linked WhatsApp number.
 *
 * <p>Stated as "these products, and only these" rather than as a list of individual changes,
 * because the screen that sends it edits one number at a time and a product removed from that
 * number has to have its mapping cleared. Expressing that as changes would mean the client
 * working out which products it just deselected, which the page cannot do: the Modlix expression
 * engine has no way to difference two lists. So the server is given the desired end state and
 * works out the difference itself.
 *
 * <p>An empty {@code products} is meaningful and is not the same as sending nothing: it means the
 * number no longer serves any product in particular, and everything on it falls back to whichever
 * number is marked default.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProductWhatsappNumberRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3120794216556011963L;

    /** The linked number, by its session code. */
    private String whatsappSessionCode;

    /** Every product that should send from it after this call. */
    private List<Identity> products;
}
