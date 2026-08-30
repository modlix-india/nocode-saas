package com.fincity.security.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.runtime.expression.Expression;
import com.fincity.nocode.kirun.engine.runtime.expression.ExpressionToken;
import com.fincity.nocode.kirun.engine.runtime.expression.Operation;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.model.authority.AuthorityCondition;
import com.fincity.security.model.authority.AuthorityExpression;
import com.fincity.security.model.authority.AuthorityGroup;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Takes an authority expression apart into the two-level shape the builder UI
 * edits: an ordered list of groups, each an ordered list of lines.
 *
 * It parses with the SAME classes that evaluate these strings at request time
 * ({@link Expression}, used by
 * {@code SecurityContextUtil.hasAuthority}), so what the builder shows can never
 * disagree with what the gate actually does. Splitting the string by hand in the
 * browser cannot make that promise: `and` and `or` appear inside parentheses,
 * precedence decides the tree, and `A and B or C` is not `A and (B or C)`.
 *
 * Parentheses are not kept in the parsed tree, so they are reconstructed from
 * precedence: an operand that binds LOOSER than its parent could only have got
 * there in brackets, and is therefore a group of its own.
 */
@Service
public class AuthorityExpressionService {

    /**
     * A token the evaluator can actually resolve. `AuthoritiesTokenExtractor`
     * compares the whole token with String.equals and the lexer's identifier set
     * is [A-Za-z0-9_], so a dotted run of identifiers is the only atom shape
     * that can ever be true.
     */
    private static final Pattern ATOM = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");

    private static final String AND = "and";
    private static final String OR = "or";

    private static final String TOO_DEEP = "This expression nests brackets more than two levels deep, which the builder cannot draw. Edit it as text.";

    @PreAuthorize("hasAuthority('Authorities.Logged_IN')")
    public Mono<AuthorityExpression> parse(String expression) {
        return Mono.fromSupplier(() -> this.parseInternal(expression))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthorityExpressionService.parse"));
    }

    AuthorityExpression parseInternal(String expression) {

        if (StringUtil.safeIsBlank(expression))
            return new AuthorityExpression().setSupported(true)
                    .setGroups(new ArrayList<>(List.of(new AuthorityGroup())));

        Expression parsed;
        try {
            parsed = new Expression(expression.trim());
        } catch (Exception ex) { // NOSONAR - any parse failure is reported the same way
            return AuthorityExpression.unsupported("This is not a valid expression: " + ex.getMessage());
        }

        List<ExpressionToken> chain = new ArrayList<>();
        List<Operation> joins = new ArrayList<>();
        this.flattenChain(parsed, chain, joins);

        // A run of bare atoms all joined by the SAME operator is one group with
        // that operator — "any of these three roles" should read as one bracket,
        // not three. Only a run that mixes the two has to be split, and then
        // precedence decides where: `and` binds tighter, so the `or` is the seam.
        boolean mixedRun = this.isMixed(chain, joins);
        String runOp = joins.isEmpty() ? AND : joins.get(0).getOperator();

        List<AuthorityGroup> groups = new ArrayList<>();
        AuthorityGroup run = null;

        for (int i = 0; i < chain.size(); i++) {

            String join = i == 0 ? AND : joins.get(i - 1).getOperator();
            Peeled peeled = this.peelNot(chain.get(i));
            Operation inner = this.opOf(peeled.node());

            if (inner == Operation.AND || inner == Operation.OR) {

                if (peeled.negated())
                    return AuthorityExpression.unsupported(
                            "A whole bracket cannot be negated here. Put the not on each line inside it instead.");

                AuthorityGroup group = this.toGroup(peeled.node(), join);
                if (group == null)
                    return AuthorityExpression.unsupported(TOO_DEEP);

                groups.add(group);
                run = null;
                continue;
            }

            String atom = this.atomOf(peeled.node());
            if (atom == null)
                return AuthorityExpression.unsupported(
                        "\"" + peeled.node() + "\" is not an authority token, so the builder cannot show it.");

            if (run == null || (mixedRun && OR.equals(join))) {
                run = new AuthorityGroup().setJ(join).setOp(mixedRun ? AND : runOp);
                groups.add(run);
            }

            run.getRows().add(new AuthorityCondition()
                    .setN(peeled.negated())
                    .setA(atom));
        }

        return new AuthorityExpression().setSupported(true).setGroups(groups);
    }

    /**
     * Builds one group from a bracketed and/or chain, or null when the bracket is
     * not one flat level — either because a line is itself a bracket, or because
     * the bracket mixes and with or, which would need brackets inside brackets.
     */
    private AuthorityGroup toGroup(ExpressionToken node, String join) {

        List<ExpressionToken> sub = new ArrayList<>();
        List<Operation> subJoins = new ArrayList<>();
        this.flattenChain(node, sub, subJoins);

        if (this.isMixed(sub, subJoins))
            return null;

        AuthorityGroup group = new AuthorityGroup().setJ(join)
                .setOp(subJoins.isEmpty() ? AND : subJoins.get(0).getOperator());

        for (ExpressionToken each : sub) {
            Peeled peeled = this.peelNot(each);
            String atom = this.atomOf(peeled.node());
            if (atom == null)
                return null;
            group.getRows().add(new AuthorityCondition()
                    .setN(peeled.negated())
                    .setA(atom));
        }

        return group;
    }

    /**
     * True when the chain of bare atoms uses both operators. Any node that is
     * itself a bracket counts as mixed, because such a chain cannot collapse into
     * one homogeneous group.
     */
    private boolean isMixed(List<ExpressionToken> chain, List<Operation> joins) {

        if (joins.size() < 2)
            return false;

        for (ExpressionToken each : chain) {
            Operation inner = this.opOf(this.peelNot(each).node());
            if (inner == Operation.AND || inner == Operation.OR)
                return true;
        }

        Operation first = joins.get(0);
        for (Operation each : joins)
            if (each != first)
                return true;

        return false;
    }

    /**
     * Walks the left spine of an and/or chain, appending each operand in reading
     * order. It stops descending when the left operand binds LOOSER than its
     * parent, because only brackets could have put it there — that operand stays
     * whole and becomes a group.
     */
    private void flattenChain(ExpressionToken node, List<ExpressionToken> out, List<Operation> joins) {

        Operation op = this.opOf(node);
        if (op != Operation.AND && op != Operation.OR) {
            out.add(node);
            return;
        }

        Expression expression = (Expression) node;
        ExpressionToken left = this.leftOf(expression);
        ExpressionToken right = this.rightOf(expression);

        Operation leftOp = this.opOf(left);
        boolean leftIsChain = leftOp == Operation.AND || leftOp == Operation.OR;

        if (leftIsChain && Operation.OPERATOR_PRIORITY.get(leftOp) <= Operation.OPERATOR_PRIORITY.get(op))
            this.flattenChain(left, out, joins);
        else
            out.add(left);

        joins.add(op);
        out.add(right);
    }

    private Peeled peelNot(ExpressionToken token) {
        Operation op = this.opOf(token);
        if (op == Operation.UNARY_LOGICAL_NOT || op == Operation.NOT)
            return new Peeled(((Expression) token).getTokensArray()[0], true);
        return new Peeled(token, false);
    }

    /**
     * Rebuilds a dotted token from the tree rather than from
     * {@code toString()}, which wraps every sub-expression in parentheses —
     * `Authorities.Logged_IN` comes back as `(Authorities.Logged_IN)`. Walking the
     * OBJECT_OPERATOR chain also means an array index or any other operator is
     * rejected structurally instead of by pattern-matching its text.
     */
    private String atomOf(ExpressionToken token) {

        if (token == null)
            return null;

        if (!(token instanceof Expression expression))
            return this.validAtom(token.toString());

        Operation[] ops = expression.getOpsArray();
        ExpressionToken[] tokens = expression.getTokensArray();

        if (ops.length == 0)
            return tokens.length == 1 ? this.atomOf(tokens[0]) : null;

        if (ops.length != 1 || ops[0] != Operation.OBJECT_OPERATOR || tokens.length != 2)
            return null;

        String left = this.atomOf(tokens[1]);
        String right = this.atomOf(tokens[0]);
        if (left == null || right == null)
            return null;

        return this.validAtom(left + "." + right);
    }

    private String validAtom(String text) {
        if (text == null)
            return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty())
            return null;
        if ("true".equals(trimmed) || "false".equals(trimmed))
            return trimmed;
        return ATOM.matcher(trimmed).matches() ? trimmed : null;
    }

    /** The single operation on a parser-built node, or null for a leaf. */
    private Operation opOf(ExpressionToken token) {
        if (!(token instanceof Expression expression))
            return null;
        Operation[] ops = expression.getOpsArray();
        return ops.length == 1 ? ops[0] : null;
    }

    /**
     * The parser pushes left then right, and push adds to the head, so index 0 is
     * the RIGHT operand and index 1 the left.
     */
    private ExpressionToken rightOf(Expression expression) {
        return expression.getTokensArray()[0];
    }

    private ExpressionToken leftOf(Expression expression) {
        return expression.getTokensArray()[1];
    }

    private record Peeled(ExpressionToken node, boolean negated) {
    }
}
