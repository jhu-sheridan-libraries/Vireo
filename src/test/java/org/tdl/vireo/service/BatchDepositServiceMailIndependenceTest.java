package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.mail.MessagingException;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.mock.BatchTestGenerators;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.EmailTemplate;
import org.tdl.vireo.model.NamedSearchFilterGroup;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionListColumn;
import org.tdl.vireo.model.SubmissionStatus;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.BatchDepositResult;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.depositor.Depositor;
import org.tdl.vireo.model.export.ExportPackage;
import org.tdl.vireo.model.packager.Packager;
import org.tdl.vireo.model.repo.DepositLocationRepo;
import org.tdl.vireo.model.repo.EmailTemplateRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;
import org.tdl.vireo.utility.TemplateUtility;

/**
 * Feature: batch-deposit-summary-email, Property 10: Mail outcome never changes the batch result
 *
 * For any batch and any mail behavior (send succeeds, sender throws
 * MessagingException, sender throws MailException, address blank, template
 * missing), the returned result and all persisted submission changes are
 * identical to a run with a working sender, and no exception propagates.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 4.9**
 */
public class BatchDepositServiceMailIndependenceTest {

    private static final long BASE_SEED = 20240610L;

    private static final int ITERATIONS = 100;

    private static final int MAX_BATCH = 15;

    private static final long DEPOSIT_LOCATION_ID = 7L;

    private enum MailBehavior {
        SEND_SUCCEEDS, MESSAGING_EXCEPTION, MAIL_EXCEPTION, BLANK_ADDRESS, MISSING_TEMPLATE
    }

    /** What happened to one submission and its outcome, comparable across runs. */
    private static final class Snapshot {
        final List<String> outcomes = new ArrayList<>();
        final List<String> submissionState = new ArrayList<>();
        int deposited;
        int failed;
        int total;
        String preconditionError;
        int depositAttempts;
        int workflowEmails;
    }

    @Test
    public void mailOutcomeNeverChangesBatchResult() {
        // Feature: batch-deposit-summary-email, Property 10: Mail outcome never changes the batch result
        BatchTestGenerators.runIterations(BASE_SEED, ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + BASE_SEED + ", iteration=" + iteration + "]";
            int size = Math.min(gen.sizeForIteration(iteration), MAX_BATCH);

            // Scenario shared by every mail behavior within this iteration.
            int[] mode = new int[size]; // 0 success, 1 deposit fails, 2 status update fails
            boolean[] workflowEmailFails = new boolean[size];
            String[] priorUrl = new String[size];
            for (int i = 0; i < size; i++) {
                mode[i] = gen.random().nextInt(3);
                workflowEmailFails[i] = gen.random().nextBoolean();
                priorUrl[i] = gen.random().nextBoolean() ? null : "http://prior.example.org/" + i;
            }
            int blankKind = gen.random().nextInt(3);
            boolean blankViaPreferred = gen.random().nextBoolean();

            Snapshot baseline = run(MailBehavior.SEND_SUCCEEDS, size, mode, workflowEmailFails, priorUrl, blankKind, blankViaPreferred, ctx);
            assertEquals(size, baseline.total, "baseline total" + ctx);

            for (MailBehavior behavior : MailBehavior.values()) {
                if (behavior == MailBehavior.SEND_SUCCEEDS) {
                    // The baseline already is a SEND_SUCCEEDS run (including its sender verification).
                    continue;
                }
                Snapshot actual = run(behavior, size, mode, workflowEmailFails, priorUrl, blankKind, blankViaPreferred, ctx);
                String bctx = " behavior=" + behavior + ctx;
                assertEquals(baseline.deposited, actual.deposited, "deposited count" + bctx);
                assertEquals(baseline.failed, actual.failed, "failed count" + bctx);
                assertEquals(baseline.total, actual.total, "total count" + bctx);
                assertEquals(baseline.preconditionError, actual.preconditionError, "precondition error" + bctx);
                assertEquals(baseline.outcomes, actual.outcomes, "outcomes" + bctx);
                assertEquals(baseline.submissionState, actual.submissionState, "persisted submission changes" + bctx);
                assertEquals(baseline.depositAttempts, actual.depositAttempts, "deposit attempts" + bctx);
                assertEquals(baseline.workflowEmails, actual.workflowEmails, "workflow emails" + bctx);
            }
        });
    }

    private Snapshot run(MailBehavior behavior, int size, int[] mode, boolean[] workflowEmailFails, String[] priorUrl, int blankKind, boolean blankViaPreferred, String ctx) {
        SubmissionStatusRepo statusRepo = mock(SubmissionStatusRepo.class);
        DepositLocationRepo locationRepo = mock(DepositLocationRepo.class);
        DepositorService depositorService = mock(DepositorService.class);
        SubmissionRepo submissionRepo = mock(SubmissionRepo.class);
        PackagerUtility packagerUtility = mock(PackagerUtility.class);
        SubmissionEmailService submissionEmailService = mock(SubmissionEmailService.class);
        EmailTemplateRepo emailTemplateRepo = mock(EmailTemplateRepo.class);
        VireoEmailSender emailSender = mock(VireoEmailSender.class);
        Depositor depositor = mock(Depositor.class);
        DepositLocation location = mock(DepositLocation.class);
        Packager packager = mock(Packager.class);
        User user = mock(User.class);
        SubmissionStatus published = mock(SubmissionStatus.class);
        SubmissionStatus prior = mock(SubmissionStatus.class);

        lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(published);
        lenient().when(locationRepo.findById(DEPOSIT_LOCATION_ID)).thenReturn(Optional.of(location));
        lenient().when(location.getName()).thenReturn("Repository");
        lenient().when(location.getDepositorName()).thenReturn("depositor");
        lenient().when(location.getPackager()).thenReturn(packager);
        lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
        lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
        lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());
        lenient().when(user.getName()).thenReturn("Rita Reviewer");
        lenient().when(user.getId()).thenReturn(42L);

        // Reviewer address: usable unless the behavior is BLANK_ADDRESS.
        String address = "reviewer@example.org";
        if (behavior == MailBehavior.BLANK_ADDRESS) {
            address = blankKind == 0 ? null : blankKind == 1 ? "" : "   \t ";
        }
        if (behavior == MailBehavior.BLANK_ADDRESS && blankViaPreferred) {
            lenient().when(user.getSetting("preferedEmail")).thenReturn(address == null ? "" : address);
            lenient().when(user.getEmail()).thenReturn("ignored@example.org");
        } else {
            lenient().when(user.getSetting("preferedEmail")).thenReturn(null);
            lenient().when(user.getEmail()).thenReturn(address);
        }

        // Summary template: present unless the behavior is MISSING_TEMPLATE.
        if (behavior != MailBehavior.MISSING_TEMPLATE) {
            EmailTemplate template = mock(EmailTemplate.class);
            lenient().when(template.getSubject()).thenReturn("Summary {DEPOSITED_COUNT}/{FAILED_COUNT}");
            lenient().when(template.getMessage()).thenReturn("{DEPOSIT_LOCATION}\n{FAILED_LIST}\n{DEPOSITED_LIST}");
            lenient().when(emailTemplateRepo.findByNameAndSystemRequired(BatchDepositSummaryEmailService.TEMPLATE_NAME, false)).thenReturn(null);
            lenient().when(emailTemplateRepo.findByNameAndSystemRequired(BatchDepositSummaryEmailService.TEMPLATE_NAME, true)).thenReturn(template);
        }

        try {
            if (behavior == MailBehavior.MESSAGING_EXCEPTION) {
                doThrow(new MessagingException("smtp down")).when(emailSender).sendEmail(anyString(), anyString(), anyString());
            } else if (behavior == MailBehavior.MAIL_EXCEPTION) {
                doThrow(new MailSendException("mail failure")).when(emailSender).sendEmail(anyString(), anyString(), anyString());
            }
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }

        List<Submission> submissions = new ArrayList<>(size);
        Map<ExportPackage, Integer> indexByPackage = new IdentityHashMap<>();
        Map<Submission, ExportPackage> packages = new IdentityHashMap<>();
        for (int i = 0; i < size; i++) {
            Submission submission = new Submission();
            ReflectionTestUtils.setField(submission, "id", 1L + i);
            submission.setSubmissionStatus(prior);
            submission.setDepositURL(priorUrl[i]);
            ExportPackage exportPackage = mock(ExportPackage.class);
            submissions.add(submission);
            packages.put(submission, exportPackage);
            indexByPackage.put(exportPackage, i);
            if (workflowEmailFails[i]) {
                doThrow(new RuntimeException("workflow email failed " + i)).when(submissionEmailService).sendWorkflowEmails(eq(user), eq(1L + i));
            }
        }
        lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
        lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenAnswer(inv -> packages.get(inv.getArgument(1)));
        lenient().when(depositor.deposit(any(), any())).thenAnswer(inv -> {
            int index = indexByPackage.get(inv.<ExportPackage>getArgument(1));
            if (mode[index] == 1) {
                throw new RuntimeException("deposit failed " + index);
            }
            return "http://deposited.example.org/" + index;
        });
        lenient().when(submissionRepo.updateStatus(any(Submission.class), any(), any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            int index = (int) (s.getId() - 1L);
            if (mode[index] == 2) {
                throw new IllegalStateException("status update failed " + index);
            }
            s.setSubmissionStatus(inv.getArgument(1));
            return s;
        });

        // Real summary email service, so each mail behavior is exercised for real.
        BatchDepositSummaryEmailService summaryEmailService = new BatchDepositSummaryEmailService();
        ReflectionTestUtils.setField(summaryEmailService, "emailTemplateRepo", emailTemplateRepo);
        ReflectionTestUtils.setField(summaryEmailService, "templateUtility", new TemplateUtility());
        ReflectionTestUtils.setField(summaryEmailService, "emailSender", emailSender);

        BatchDepositService service = new BatchDepositService();
        ReflectionTestUtils.setField(service, "submissionStatusRepo", statusRepo);
        ReflectionTestUtils.setField(service, "depositLocationRepo", locationRepo);
        ReflectionTestUtils.setField(service, "depositorService", depositorService);
        ReflectionTestUtils.setField(service, "submissionRepo", submissionRepo);
        ReflectionTestUtils.setField(service, "packagerUtility", packagerUtility);
        ReflectionTestUtils.setField(service, "submissionEmailService", submissionEmailService);
        ReflectionTestUtils.setField(service, "batchDepositSummaryEmailService", summaryEmailService);

        AtomicReference<BatchDepositResult> holder = new AtomicReference<>();
        assertDoesNotThrow(() -> holder.set(service.publish(user, DEPOSIT_LOCATION_ID)), "no exception propagates, behavior=" + behavior + ctx);
        BatchDepositResult result = holder.get();

        // The sender is called exactly once when it can be reached, never otherwise.
        try {
            boolean reachable = behavior == MailBehavior.SEND_SUCCEEDS || behavior == MailBehavior.MESSAGING_EXCEPTION || behavior == MailBehavior.MAIL_EXCEPTION;
            if (reachable) {
                verify(emailSender, times(1)).sendEmail(eq("reviewer@example.org"), anyString(), anyString());
            } else {
                verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString());
            }
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }

        Snapshot snapshot = new Snapshot();
        snapshot.deposited = result.deposited();
        snapshot.failed = result.failed();
        snapshot.total = result.total();
        snapshot.preconditionError = result.getPreconditionError();
        for (DepositOutcome o : result.getOutcomes()) {
            snapshot.outcomes.add(o.getSubmissionId() + "|" + o.isDeposited() + "|" + o.getTitle() + "|" + o.getFailureReason());
        }
        for (Submission s : submissions) {
            snapshot.submissionState.add(s.getId() + "|" + (s.getSubmissionStatus() == published ? "published" : "prior") + "|" + s.getDepositURL());
        }
        snapshot.depositAttempts = org.mockito.Mockito.mockingDetails(depositor).getInvocations().size();
        snapshot.workflowEmails = org.mockito.Mockito.mockingDetails(submissionEmailService).getInvocations().size();
        return snapshot;
    }

}
