package org.tdl.vireo.model.batch;

/**
 * The result of attempting to deposit a single submission during a batch run.
 *
 * Plain in-memory value; never persisted.
 */
public final class DepositOutcome {

    private final long submissionId;

    private final String title;

    private final boolean deposited;

    private final String failureReason;

    private final Throwable cause;

    public DepositOutcome(long submissionId, String title, boolean deposited, String failureReason, Throwable cause) {
        this.submissionId = submissionId;
        this.title = title == null || title.trim().isEmpty() ? null : title;
        this.deposited = deposited;
        this.failureReason = failureReason;
        this.cause = cause;
    }

    public static DepositOutcome deposited(long submissionId, String title) {
        return new DepositOutcome(submissionId, title, true, null, null);
    }

    public static DepositOutcome failed(long submissionId, String title, String failureReason, Throwable cause) {
        return new DepositOutcome(submissionId, title, false, failureReason, cause);
    }

    public long getSubmissionId() {
        return submissionId;
    }

    /**
     * @return the submission title, or null when absent or blank.
     */
    public String getTitle() {
        return title;
    }

    public boolean isDeposited() {
        return deposited;
    }

    /**
     * @return the failure reason, or null when the submission was deposited.
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * @return the exception that caused the failure, or null when the submission was deposited.
     */
    public Throwable getCause() {
        return cause;
    }

}
