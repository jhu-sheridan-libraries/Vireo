package org.tdl.vireo.model.batch;

/**
 * Describes a batch deposit run that stopped early because an {@link Error} was
 * thrown while attempting one submission.
 *
 * Plain in-memory value; never persisted.
 */
public final class BatchStop {

    private final long submissionId;

    private final int notAttempted;

    /**
     * @param submissionId the submission being attempted when the error was thrown
     * @param notAttempted the number of submissions after it that were not attempted
     */
    public BatchStop(long submissionId, int notAttempted) {
        this.submissionId = submissionId;
        this.notAttempted = notAttempted;
    }

    public long getSubmissionId() {
        return submissionId;
    }

    public int getNotAttempted() {
        return notAttempted;
    }

}
