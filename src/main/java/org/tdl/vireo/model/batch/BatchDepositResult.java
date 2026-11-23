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

    /**
     * @param message why the run could not start; must not be null or blank, because a
     *                missing message would make the result look like a run that started
     * @throws IllegalArgumentException when the message is null or blank
     */
    public static BatchDepositResult preconditionFailed(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("A precondition failure needs a message");
        }
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
        return count(DepositOutcome.Status.DEPOSITED);
    }

    public int failed() {
        return count(DepositOutcome.Status.FAILED);
    }

    /**
     * @return the number of submissions deposited to the repository but not recorded in Vireo.
     */
    public int notRecorded() {
        return count(DepositOutcome.Status.NOT_RECORDED);
    }

    private int count(DepositOutcome.Status status) {
        int count = 0;
        for (DepositOutcome outcome : outcomes) {
            if (outcome.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    public int total() {
        return outcomes.size();
    }

}
