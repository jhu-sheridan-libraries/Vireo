package org.tdl.vireo.model.batch;

/**
 * The result of attempting to deposit a single submission during a batch run.
 */
public final class DepositOutcome {

    public enum Status {
        /** Deposited to the repository and recorded as Published in Vireo. */
        DEPOSITED,
        /** Not deposited to the repository. Safe to retry. */
        FAILED,
        /**
         * Deposited to the repository, but Vireo could not record the deposit.
         * The item exists remotely, so retrying would create a duplicate.
         */
        NOT_RECORDED
    }

    private final long submissionId;

    private final String title;

    private final Status status;

    private final String depositURL;

    private final String failureReason;

    private final Throwable cause;

    private DepositOutcome(long submissionId, String title, Status status, String depositURL, String failureReason, Throwable cause) {
        this.submissionId = submissionId;
        this.title = title == null || title.trim().isEmpty() ? null : title;
        this.status = status;
        this.depositURL = depositURL;
        this.failureReason = failureReason;
        this.cause = cause;
    }

    public static DepositOutcome deposited(long submissionId, String title) {
        return new DepositOutcome(submissionId, title, Status.DEPOSITED, null, null, null);
    }

    public static DepositOutcome failed(long submissionId, String title, String failureReason, Throwable cause) {
        return new DepositOutcome(submissionId, title, Status.FAILED, null, failureReason, cause);
    }

    public static DepositOutcome notRecorded(long submissionId, String title, String depositURL, String failureReason, Throwable cause) {
        return new DepositOutcome(submissionId, title, Status.NOT_RECORDED, depositURL, failureReason, cause);
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

    public Status getStatus() {
        return status;
    }

    public boolean isDeposited() {
        return status == Status.DEPOSITED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isNotRecorded() {
        return status == Status.NOT_RECORDED;
    }

    /**
     * @return the URL returned by the repository for a {@link Status#NOT_RECORDED}
     *         outcome (may be null if the repository returned none), otherwise null.
     */
    public String getDepositURL() {
        return depositURL;
    }

    /**
     * @return the failure reason, or null when the submission was deposited.
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * @return the exception that caused the failure, or null when deposited or not attempted.
     */
    public Throwable getCause() {
        return cause;
    }

}
