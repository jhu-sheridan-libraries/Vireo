package org.tdl.vireo.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.EmailTemplate;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.BatchDepositSummaryBuilder;
import org.tdl.vireo.model.batch.BatchStop;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.SummaryEmailResult;
import org.tdl.vireo.model.repo.EmailTemplateRepo;
import org.tdl.vireo.utility.TemplateUtility;

/**
 * Sends the summary of a batch deposit run to the reviewer who started it.
 *
 * Sending is best effort: {@link #send} never throws, so a mail problem can
 * never affect the result of the batch run.
 */
@Service
public class BatchDepositSummaryEmailService {

    public static final String TEMPLATE_NAME = "SYSTEM Batch Deposit Summary";

    static final String NOT_RECORDED_LIST_PLACEHOLDER = "{NOT_RECORDED_LIST}";

    private static final Logger LOG = LoggerFactory.getLogger(BatchDepositSummaryEmailService.class);

    @Autowired
    private EmailTemplateRepo emailTemplateRepo;

    @Autowired
    private TemplateUtility templateUtility;

    @Autowired
    private VireoEmailSender emailSender;

    public SummaryEmailResult send(User reviewer, DepositLocation location, List<DepositOutcome> outcomes, ZonedDateTime finishedAt) {
        return send(reviewer, location, outcomes, finishedAt, null);
    }

    /**
     * @param stop when not null, the run stopped early and the summary says so.
     * @return whether the email was sent, and if not, why; never throws for these cases
     */
    public SummaryEmailResult send(User reviewer, DepositLocation location, List<DepositOutcome> outcomes, ZonedDateTime finishedAt, BatchStop stop) {
        int deposited = 0;
        int failed = 0;
        int notRecorded = 0;
        try {
            if (outcomes != null) {
                for (DepositOutcome outcome : outcomes) {
                    if (outcome.isDeposited()) {
                        deposited++;
                    } else if (outcome.isNotRecorded()) {
                        notRecorded++;
                    } else {
                        failed++;
                    }
                }
            }

            String address = reviewerAddress(reviewer);
            if (address == null || address.trim().isEmpty()) {
                LOG.warn("Batch deposit summary not sent: reviewer {} has no email address.", reviewerName(reviewer));
                return SummaryEmailResult.NO_ADDRESS;
            }

            EmailTemplate template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, false);
            if (template == null) {
                template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);
            }
            if (template == null) {
                LOG.error("Batch deposit summary not sent: email template '{}' was not found.", TEMPLATE_NAME);
                return SummaryEmailResult.NO_TEMPLATE;
            }

            // A template saved before NOT_RECORDED_LIST existed (an older system template kept as a
            // custom copy, or an administrator's edit) still has to show those submissions.
            boolean legacyTemplate = template.getMessage() == null || !template.getMessage().contains(NOT_RECORDED_LIST_PLACEHOLDER);
            if (legacyTemplate) {
                LOG.warn("Batch deposit summary email template '{}' has no {} placeholder. Deposited but not recorded submissions are listed under failed deposits. Update the template.", TEMPLATE_NAME, NOT_RECORDED_LIST_PLACEHOLDER);
            }

            Map<String, String> values = new BatchDepositSummaryBuilder()
                .build(location == null ? null : location.getName(), outcomes, finishedAt, legacyTemplate, stop)
                .getParameters();

            // The subject is a mail header: it must never contain a line break, whatever the template holds.
            String subject = singleLine(templateUtility.templateLiteral(template.getSubject(), values));
            String message = templateUtility.templateLiteral(template.getMessage(), values);

            emailSender.sendEmail(address, subject, message);
            return SummaryEmailResult.SENT;
        } catch (MessagingException | RuntimeException e) {
            // RuntimeException includes Spring's MailException (org.springframework.mail).
            logSendFailure(reviewer, deposited, failed, notRecorded, e);
            return SummaryEmailResult.SEND_FAILED;
        }
    }

    private static String singleLine(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]+", " ");
    }

    private static String reviewerAddress(User reviewer) {
        if (reviewer == null) {
            return null;
        }
        String preferred = reviewer.getSetting("preferedEmail");
        return preferred == null ? reviewer.getEmail() : preferred;
    }

    private static String reviewerName(User reviewer) {
        return reviewer == null ? "unknown" : reviewer.getName() + " (id " + reviewer.getId() + ")";
    }

    private static void logSendFailure(User reviewer, int deposited, int failed, int notRecorded, Exception e) {
        LOG.error("Batch deposit summary email to reviewer {} failed (Deposited: {}, Failed: {}, Not recorded: {}).", reviewerName(reviewer), deposited, failed, notRecorded, e);
    }

}
