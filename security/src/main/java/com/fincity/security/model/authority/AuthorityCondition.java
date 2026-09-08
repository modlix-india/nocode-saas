package com.fincity.security.model.authority;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One line of an authority expression, as the builder UI models it.
 *
 * A line carries no operator of its own: how the lines of a group combine is a
 * property of the {@link AuthorityGroup}, because a bracket holding a mixture
 * of and and or would need brackets of its own and stops being one level.
 *
 * The field names are deliberately one letter: this object is written straight
 * into the page store and bound to components by path, so the wire shape IS the
 * builder's model. Renaming these breaks the bindings in the appbuilder
 * workspace's Access tab.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 7215487412198765401L;

    /** Whether the line is negated. */
    private boolean n;

    /** The authority token, e.g. Authorities.ROLE_Owner. */
    private String a;
}
