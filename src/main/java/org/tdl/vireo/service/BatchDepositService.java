package org.tdl.vireo.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionStatus;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.BatchDepositResult;
import org.tdl.vireo.model.batch.BatchDepositSummaryBuilder;
import org.tdl.vireo.model.batch.BatchStop;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.FailureReason;
import org.tdl.vireo.model.batch.SummaryEmailResult;
import org.tdl.vireo.model.depositor.Depositor;
import org.tdl.vireo.model.export.ExportPackage;
import org.tdl.vireo.model.repo.DepositLocationRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;
import org.tdl.vireo.utility.SubmissionHelperUtility;

/**
 * Runs one batch deposit. Each matched submission is attempted in order, so one failure never
 * blocks the rest, and one best-effort summary email goes to the reviewer. An {@link Error} stops
 * the run: what is known is reported, then the Error is rethrown.
 */
@Service
public class BatchDepositService {

    public static final String PUBLISHED_STATUS_NAME = "Published";

    private static final Logger LOG = LoggerFactory.getLogger(BatchDepositService.class);

    @Autowired
    private SubmissionStatusRepo submissionStatusRepo;

    @Autowired
    private DepositLocationRepo depositLocationRepo;

    @Autowired
    private DepositorService depositorService;

    @Autowired
    private SubmissionRepo submissionRepo;

    @Autowired
    private PackagerUtility packagerUtility;

    @Autowired
    private SubmissionEmailService submissionEmailService;

    @Autowired
    private BatchDepositSummaryEmailService batchDepositSummaryEmailService;

    /**
     * Publishes every submission matched by the reviewer's active filter to the
     * given deposit location.
     *
     * @return the result of the run, or a precondition failure when the run
     *         could not start
     */
    public BatchDepositResult publish(User user, Long depositLocationId) {

        SubmissionStatus publishedStatus = submissionStatusRepo.findByName(PUBLISHED_STATUS_NAME);
        if (publishedStatus == null) {
            return BatchDepositResult.preconditionFailed("Could not find a submission status named " + PUBLISHED_STATUS_NAME);
        }

        DepositLocation depositLocation = depositLocationRepo.findById(depositLocationId).orElse(null);
        if (depositLocation == null) {
            return BatchDepositResult.preconditionFailed("Could not find a deposit location with id " + depositLocationId);
        }

        Depositor depositor = depositorService.getDepositor(depositLocation.getDepositorName());
        if (depositor == null) {
            return BatchDepositResult.preconditionFailed("Could not find a depositor named " + depositLocation.getDepositorName());
        }

        // Materialize the matched submissions once so the attempted set cannot change mid-run.
        List<Submission> submissions = new ArrayList<Submission>(
            submissionRepo.batchDynamicSubmissionQuery(user.getActiveFilter(), user.getSubmissionViewColumns()));

        List<DepositOutcome> outcomes = new ArrayList<DepositOutcome>(submissions.size());
        Error stopError = null;
        BatchStop stop = null;
        for (int i = 0; i < submissions.size(); i++) {
            Attempt attempt = attempt(user, depositLocation, depositor, publishedStatus, submissions.get(i));
            outcomes.add(attempt.outcome);
            if (attempt.error != null) {
                // The JVM may be in a bad state (for example out of memory), so do not attempt
                // anything else. Report the rest as not attempted; they are safe to retry.
                stopError = attempt.error;
                long stoppedOn = attempt.outcome.getSubmissionId();
                List<Submission> rest = submissions.subList(i + 1, submissions.size());
                String reason = BatchDepositSummaryBuilder.notAttemptedReason(stoppedOn);
                for (Submission notAttempted : rest) {
                    outcomes.add(DepositOutcome.failed(notAttempted.getId(), titleAfterStop(notAttempted), reason, null));
                }
                stop = new BatchStop(stoppedOn, rest.size());
                LOG.error("Batch publish stopped after an internal error on submission {} (outcome: {}). {} submissions were not attempted.",
                    stoppedOn, attempt.outcome.getStatus(), rest.size(), stopError);
                break;
            }
        }

        ZonedDateTime finishedAt = ZonedDateTime.now();

        if (stopError == null) {
            SummaryEmailResult summaryEmail = batchDepositSummaryEmailService.send(user, depositLocation, outcomes, finishedAt);
            return new BatchDepositResult(outcomes, summaryEmail);
        }

        // Send what is known, then rethrow so the container sees the Error. A second Error
        // while sending must not replace the original.
        try {
            batchDepositSummaryEmailService.send(user, depositLocation, outcomes, finishedAt, stop);
        } catch (Throwable sendError) {
            stopError.addSuppressed(sendError);
        }
        throw stopError;
    }

    /** One submission's outcome, plus the Error that must stop the batch, if any. */
    private static final class Attempt {

        private final DepositOutcome outcome;

        private final Error error;

        private Attempt(DepositOutcome outcome, Error error) {
            this.outcome = outcome;
            this.error = error;
        }

    }

    private Attempt attempt(User user, DepositLocation depositLocation, Depositor depositor, SubmissionStatus publishedStatus, Submission submission) {
        long submissionId = submission.getId();
        String title;
        try {
            title = titleOf(submission);
        } catch (Error e) {
            // Nothing was packaged or deposited yet.
            return new Attempt(DepositOutcome.failed(submissionId, null, FailureReason.of(e), e), e);
        }
        String depositURL = null;
        boolean depositedRemotely = false;

        try {
            ExportPackage exportPackage = packagerUtility.packageExport(depositLocation.getPackager(), submission);
            depositURL = depositor.deposit(depositLocation, exportPackage);
            // From here on the item exists in the repository and cannot be taken back.
            depositedRemotely = true;
            submission.setDepositURL(depositURL);
            submission = submissionRepo.updateStatus(submission, publishedStatus, user);
        } catch (Throwable t) {
            // An Error is classified like an exception, then stops the batch; publish() logs it.
            Error error = t instanceof Error ? (Error) t : null;
            if (depositedRemotely) {
                // updateStatus is not atomic: it may have failed before or after saving the
                // submission. Either way the reviewer must check the record by hand, not retry.
                // Workflow emails are skipped because the Published status may not be saved.
                if (error == null) {
                    LOG.error("Batch publish deposited submission {} to {} but could not record it in Vireo. Do not retry; check the record by hand.", submissionId, depositURL, t);
                }
                return new Attempt(DepositOutcome.notRecorded(submissionId, title, depositURL, FailureReason.of(t), t), error);
            }
            if (error == null) {
                LOG.error("Batch publish failed to deposit submission {}.", submissionId, t);
            }
            return new Attempt(DepositOutcome.failed(submissionId, title, FailureReason.of(t), t), error);
        }

        // Workflow emails are best effort; a failure here must not change the Deposited outcome.
        DepositOutcome deposited = DepositOutcome.deposited(submissionId, title);
        try {
            submissionEmailService.sendWorkflowEmails(user, submissionId);
        } catch (Exception e) {
            LOG.error("Batch publish deposited submission {} but failed to send its workflow emails.", submissionId, e);
        } catch (Error e) {
            return new Attempt(deposited, e);
        }

        return new Attempt(deposited, null);
    }

    /**
     * Reads a title for a submission the run did not reach. The run is already stopping
     * because of an Error, so nothing thrown here may replace that Error.
     */
    private String titleAfterStop(Submission submission) {
        try {
            return titleOf(submission);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Reads the title for reporting only. A title that cannot be read (for example a
     * stored null value) must not stop the submission from being attempted or the batch
     * from finishing, so it is reported as absent.
     */
    private String titleOf(Submission submission) {
        try {
            return new SubmissionHelperUtility(submission).getTitle();
        } catch (RuntimeException e) {
            LOG.warn("Batch publish could not read the title of submission {}; it will be reported without a title.", submission.getId(), e);
            return null;
        }
    }

}
