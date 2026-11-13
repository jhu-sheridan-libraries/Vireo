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
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.FailureReason;
import org.tdl.vireo.model.depositor.Depositor;
import org.tdl.vireo.model.export.ExportPackage;
import org.tdl.vireo.model.repo.DepositLocationRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;
import org.tdl.vireo.utility.SubmissionHelperUtility;

/**
 * Orchestrates one batch deposit run.
 *
 * Preconditions are validated before any deposit is attempted. Each matched
 * submission is then attempted in order, isolating per-submission failures so a
 * single bad submission never blocks the rest of the batch. After the loop a
 * single summary email is sent to the reviewer who started the run. The summary
 * email is best effort and never changes the result of the run.
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
     * @param user              the reviewer who started the run
     * @param depositLocationId the id of the deposit location to publish to
     * @return the result of the run, or a precondition failure when the run
     *         could not start
     */
    public BatchDepositResult publish(User user, Long depositLocationId) {

        // Preconditions, in order: Published status, deposit location, depositor.
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
        for (Submission submission : submissions) {
            outcomes.add(attempt(user, depositLocation, depositor, publishedStatus, submission));
        }

        ZonedDateTime finishedAt = ZonedDateTime.now();
        batchDepositSummaryEmailService.send(user, depositLocation, outcomes, finishedAt);

        return new BatchDepositResult(outcomes);
    }

    private DepositOutcome attempt(User user, DepositLocation depositLocation, Depositor depositor, SubmissionStatus publishedStatus, Submission submission) {
        String title = new SubmissionHelperUtility(submission).getTitle();
        String previousDepositURL = submission.getDepositURL();
        boolean depositURLChanged = false;

        try {
            ExportPackage exportPackage = packagerUtility.packageExport(depositLocation.getPackager(), submission);
            String depositURL = depositor.deposit(depositLocation, exportPackage);
            submission.setDepositURL(depositURL);
            depositURLChanged = true;
            submission = submissionRepo.updateStatus(submission, publishedStatus, user);
        } catch (Exception e) {
            LOG.error("Batch publish failed to deposit submission {}.", submission.getId(), e);
            if (depositURLChanged) {
                // A failure after setDepositURL must leave the persisted value unchanged.
                submission.setDepositURL(previousDepositURL);
            }
            return DepositOutcome.failed(submission.getId(), title, FailureReason.of(e), e);
        }

        // Workflow emails are best effort; a failure here must not change the Deposited outcome.
        try {
            submissionEmailService.sendWorkflowEmails(user, submission.getId());
        } catch (Exception e) {
            LOG.error("Batch publish deposited submission {} but failed to send its workflow emails.", submission.getId(), e);
        }

        return DepositOutcome.deposited(submission.getId(), title);
    }

}
