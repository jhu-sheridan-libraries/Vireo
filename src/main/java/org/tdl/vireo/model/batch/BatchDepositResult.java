package org.tdl.vireo.model.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of one batch deposit run: either the per-submission outcomes, or
 * a precondition error when the run could not start.
 *
 * Plain in-memory value; never persisted.
 */
public final class BatchDepositResult {

    private final List<DepositOutcome> outcomes;

    private final String preconditionError;

    public BatchDepositResult(List<DepositOutcome> outcomes) {
        this(outcomes, null);
    }

    private BatchDepositResult(List<DepositOutcome> outcomes, String preconditionError) {
        this.outcomes = outcomes == null ? Collections.<DepositOutcome>emptyList() : Collections.unmodifiableList(new ArrayList<DepositOutcome>(outcomes));
        this.preconditionError = preconditionError;
    }

    public static BatchDepositResult preconditionFailed(String message) {
        return new BatchDepositResult(null, message);
    }

    public List<DepositOutcome> getOutcomes() {
        return outcomes;
    }

    /**
     * @return the precondition error message, or null when the run started.
     */
    public String getPreconditionError() {
        return preconditionError;
    }

    public boolean isPreconditionFailed() {
        return preconditionError != null;
    }

    public int deposited() {
        int count = 0;
        for (DepositOutcome outcome : outcomes) {
            if (outcome.isDeposited()) {
                count++;
            }
        }
        return count;
    }

    public int failed() {
        return total() - deposited();
    }

    public int total() {
        return outcomes.size();
    }

}
