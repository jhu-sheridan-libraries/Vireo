package org.tdl.vireo.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.EmailTemplate;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.BatchDepositSummaryBuilder;
import org.tdl.vireo.model.batch.DepositOutcome;
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

    private static final Logger LOG = LoggerFactory.getLogger(BatchDepositSummaryEmailService.class);

    @Autowired
    private EmailTemplateRepo emailTemplateRepo;

    @Autowired
    private TemplateUtility templateUtility;

    @Autowired
    private VireoEmailSender emailSender;

    public void send(User reviewer, DepositLocation location, List<DepositOutcome> outcomes, ZonedDateTime finishedAt) {
        int deposited = 0;
        int failed = 0;
        try {
            if (outcomes != null) {
                for (DepositOutcome outcome : outcomes) {
                    if (outcome.isDeposited()) {
                        deposited++;
                    } else {
                        failed++;
                    }
                }
            }

            String address = reviewerAddress(reviewer);
            if (address == null || address.trim().isEmpty()) {
                LOG.warn("Batch deposit summary not sent: reviewer {} has no email address.", reviewerName(reviewer));
                return;
            }

            EmailTemplate template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, false);
            if (template == null) {
                template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);
            }
            if (template == null) {
                LOG.error("Batch deposit summary not sent: email template '{}' was not found.", TEMPLATE_NAME);
                return;
            }

            Map<String, String> values = new BatchDepositSummaryBuilder()
                .build(location == null ? null : location.getName(), outcomes, finishedAt)
                .getParameters();

            String subject = templateUtility.templateLiteral(template.getSubject(), values);
            String message = templateUtility.templateLiteral(template.getMessage(), values);

            emailSender.sendEmail(address, subject, message);
        } catch (MessagingException | MailException e) {
            logSendFailure(reviewer, deposited, failed, e);
        } catch (RuntimeException e) {
            logSendFailure(reviewer, deposited, failed, e);
        }
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

    private static void logSendFailure(User reviewer, int deposited, int failed, Exception e) {
        LOG.error("Batch deposit summary email to reviewer {} failed (Deposited: {}, Failed: {}).", reviewerName(reviewer), deposited, failed, e);
    }

}
