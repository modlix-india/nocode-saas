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
 * A bracket of the expression.
 *
 * Two different operators live here and confusing them is the whole reason this
 * class exists: {@link #op} is how the lines INSIDE the bracket combine, and
 * {@link #j} is how the bracket as a whole joins the bracket above it. A group
 * is homogeneous by construction — a bracket mixing and with or would need
 * brackets of its own, which is a level the builder cannot draw and the parser
 * refuses.
 *
 * One-letter names for the same reason as {@link AuthorityCondition}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = -2265498771003245118L;

    /** How this group joins the group above it: "and" or "or". Ignored on the first group. */
    private String j = "and";

    /** How the lines inside this group combine: "and" (all) or "or" (any). */
    private String op = "and";

    private List<AuthorityCondition> rows = new ArrayList<>();
}
