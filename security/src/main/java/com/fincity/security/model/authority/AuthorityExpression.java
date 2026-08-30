package com.fincity.security.model.authority;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The result of taking an authority expression apart.
 *
 * When {@link #supported} is false the expression is valid but says more than
 * two levels of grouping can express, so the builder must leave it alone and
 * keep offering the raw text box. {@link #reason} is meant to be shown to the
 * person editing.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityExpression implements Serializable {

    @Serial
    private static final long serialVersionUID = 5540118872103458821L;

    private boolean supported;

    private String reason;

    private List<AuthorityGroup> groups = new ArrayList<>();

    public static AuthorityExpression unsupported(String reason) {
        return new AuthorityExpression().setSupported(false).setReason(reason);
    }
}
