package com.modlix.saas.adzump.service.optimize;

import java.util.List;

/**
 * One per-dimension analyzer (J12 §5.2): reads the {@link AnalyzerContext} (the snapshot rows + plan +
 * effective policy) and proposes candidate actions for its dimension (budget / bid / audience /
 * keyword / creative). Deterministic Java ported from the legacy per-dimension optimization agents —
 * <b>no LLM, no I/O, no mutation</b>. Every implementation is a stateless Spring bean; the
 * {@link OptimizationEngine} injects them all as a {@code List} and runs each, then the
 * {@link SignificanceGate} judges the union.
 */
public interface DimensionAnalyzer {

    /** Propose candidate actions from the context (may be empty). Proposes liberally; the gate disposes. */
    List<Candidate> analyze(AnalyzerContext ctx);
}
