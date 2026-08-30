package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fincity.security.model.authority.AuthorityCondition;
import com.fincity.security.model.authority.AuthorityExpression;
import com.fincity.security.model.authority.AuthorityGroup;

class AuthorityExpressionServiceTest {

    private final AuthorityExpressionService service = new AuthorityExpressionService();

    /**
     * Mirrors exactly what the builder writes back, so a round-trip proves the
     * parse is faithful rather than merely plausible. A group is bracketed only
     * when it has more than one line AND there is more than one group, because
     * `and` already binds tighter than `or`.
     */
    private static String serialise(List<AuthorityGroup> groups) {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < groups.size(); g++) {
            AuthorityGroup group = groups.get(g);
            if (group.getRows().isEmpty())
                continue;
            boolean bracket = group.getRows().size() > 1 && groups.size() > 1;
            if (g > 0)
                sb.append(' ').append(group.getJ()).append(' ');
            if (bracket)
                sb.append('(');
            for (int r = 0; r < group.getRows().size(); r++) {
                AuthorityCondition row = group.getRows().get(r);
                if (r > 0)
                    sb.append(' ').append(group.getOp()).append(' ');
                if (row.isN())
                    sb.append("not ");
                sb.append(row.getA());
            }
            if (bracket)
                sb.append(')');
        }
        return sb.toString();
    }

    private AuthorityExpression ok(String expression) {
        AuthorityExpression parsed = service.parseInternal(expression);
        assertTrue(parsed.isSupported(), () -> "expected supported for: " + expression + " -> " + parsed.getReason());
        return parsed;
    }

    @Test
    void blankMeansOneEmptyGroup() {
        AuthorityExpression parsed = ok("");
        assertEquals(1, parsed.getGroups().size());
        assertTrue(parsed.getGroups().get(0).getRows().isEmpty());
    }

    @Test
    void singleAtom() {
        AuthorityExpression parsed = ok("Authorities.Logged_IN");
        assertEquals(1, parsed.getGroups().size());
        assertEquals(1, parsed.getGroups().get(0).getRows().size());
        assertEquals("Authorities.Logged_IN", parsed.getGroups().get(0).getRows().get(0).getA());
        assertEquals("Authorities.Logged_IN", serialise(parsed.getGroups()));
    }

    @Test
    void andChainIsOneGroup() {
        AuthorityExpression parsed = ok("Authorities.A and Authorities.B and Authorities.C");
        assertEquals(1, parsed.getGroups().size());
        assertEquals("and", parsed.getGroups().get(0).getOp());
        assertEquals(3, parsed.getGroups().get(0).getRows().size());
        assertEquals("Authorities.A and Authorities.B and Authorities.C", serialise(parsed.getGroups()));
    }

    /** "any of these three roles" must read as one bracket, not three groups. */
    @Test
    void orChainIsOneAnyGroup() {
        AuthorityExpression parsed = ok("Authorities.A or Authorities.B or Authorities.C");
        assertEquals(1, parsed.getGroups().size());
        assertEquals("or", parsed.getGroups().get(0).getOp());
        assertEquals(3, parsed.getGroups().get(0).getRows().size());
        assertEquals("Authorities.A or Authorities.B or Authorities.C", serialise(parsed.getGroups()));
    }

    @Test
    void twoAtomsJoinedByOrStayOneGroup() {
        AuthorityExpression parsed = ok("Authorities.A or Authorities.B");
        assertEquals(1, parsed.getGroups().size());
        assertEquals("or", parsed.getGroups().get(0).getOp());
        assertEquals("Authorities.A or Authorities.B", serialise(parsed.getGroups()));
    }

    @Test
    void orSplitsGroups() {
        AuthorityExpression parsed = ok("Authorities.A and Authorities.B or Authorities.C");
        assertEquals(2, parsed.getGroups().size());
        assertEquals(2, parsed.getGroups().get(0).getRows().size());
        assertEquals("or", parsed.getGroups().get(1).getJ());
        // Brackets are now explicit, which is what `and` binding tighter already meant.
        assertEquals("(Authorities.A and Authorities.B) or Authorities.C", serialise(parsed.getGroups()));
    }

    /** The case reported from the workspace: an or bracket on the right of an and. */
    @Test
    void bracketOnTheRightOfAnAnd() {
        String expression = "Authorities.Logged_IN and (Authorities.LEADZUMP.ROLE_Product_CREATE or Authorities.ROLE_Schema_Manager)";
        AuthorityExpression parsed = ok(expression);

        assertEquals(2, parsed.getGroups().size());
        assertEquals(1, parsed.getGroups().get(0).getRows().size());
        assertEquals("Authorities.Logged_IN", parsed.getGroups().get(0).getRows().get(0).getA());

        AuthorityGroup second = parsed.getGroups().get(1);
        assertEquals("and", second.getJ(), "the second group joins the first with and");
        assertEquals("or", second.getOp(), "and its own lines combine with or");
        assertEquals(2, second.getRows().size());
        assertEquals("Authorities.LEADZUMP.ROLE_Product_CREATE", second.getRows().get(0).getA());
        assertEquals("Authorities.ROLE_Schema_Manager", second.getRows().get(1).getA());

        assertEquals(expression, serialise(parsed.getGroups()));
    }

    /** A bracket on the LEFT of an and must survive, or the meaning flips. */
    @Test
    void bracketOnTheLeftOfAnAnd() {
        String expression = "(Authorities.A or Authorities.B) and Authorities.C";
        AuthorityExpression parsed = ok(expression);

        assertEquals(2, parsed.getGroups().size());
        assertEquals(2, parsed.getGroups().get(0).getRows().size());
        assertEquals("or", parsed.getGroups().get(0).getOp());
        assertEquals("and", parsed.getGroups().get(1).getJ());
        assertEquals(expression, serialise(parsed.getGroups()));
    }

    @Test
    void twoBrackets() {
        String expression = "(Authorities.A or Authorities.B) and (Authorities.C or Authorities.D)";
        AuthorityExpression parsed = ok(expression);
        assertEquals(2, parsed.getGroups().size());
        assertEquals(expression, serialise(parsed.getGroups()));
    }

    @Test
    void notIsPeeledOntoTheLine() {
        AuthorityExpression parsed = ok("not Authorities.A and Authorities.B");
        assertEquals(1, parsed.getGroups().size());
        assertTrue(parsed.getGroups().get(0).getRows().get(0).isN());
        assertFalse(parsed.getGroups().get(0).getRows().get(1).isN());
        assertEquals("not Authorities.A and Authorities.B", serialise(parsed.getGroups()));
    }

    /** A bracket that mixes the two operators is really three levels. */
    @Test
    void mixedBracketIsRefused() {
        AuthorityExpression parsed = service
                .parseInternal("Authorities.Z and (Authorities.A and Authorities.B or Authorities.C)");
        assertFalse(parsed.isSupported());
    }

    @Test
    void threeLevelsIsRefused() {
        AuthorityExpression parsed = service
                .parseInternal("Authorities.A and (Authorities.B or (Authorities.C and Authorities.D))");
        assertFalse(parsed.isSupported());
    }

    @Test
    void aComparisonIsRefused() {
        assertFalse(service.parseInternal("Authorities.A and Store.x = 1").isSupported());
    }

    @Test
    void garbageIsRefused() {
        assertFalse(service.parseInternal("Authorities.A and )(").isSupported());
    }
}
