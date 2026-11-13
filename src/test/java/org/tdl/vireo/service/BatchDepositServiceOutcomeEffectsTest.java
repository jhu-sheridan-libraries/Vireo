package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.mock.BatchTestGenerators;
import org.tdl.vireo.model.ActionLog;
import org.tdl.vireo.model.DepositLocation;
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
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;

/**
 * Feature: batch-deposit-summary-email, Property 2: Outcomes mutate only successful submissions
 *
 * **Validates: Requirements 1.2, 1.3, 1.5, 1.6**
 */
public class BatchDepositServiceOutcomeEffectsTest {

    private static final long BASE_SEED = 20240602L;

    private static final int ITERATIONS = 100;

    private static final int MAX_BATCH = 60;

    private static final long DEPOSIT_LOCATION_ID = 7L;

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void outcomesMutateOnlySuccessfulSubmissions() {
        // Feature: batch-deposit-summary-email, Property 2: Outcomes mutate only successful submissions
        BatchTestGenerators.runIterations(BASE_SEED, ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + BASE_SEED + ", iteration=" + iteration + "]";
            int size = Math.min(gen.sizeForIteration(iteration), MAX_BATCH);

            SubmissionStatusRepo statusRepo = mock(SubmissionStatusRepo.class);
            DepositLocationRepo locationRepo = mock(DepositLocationRepo.class);
            DepositorService depositorService = mock(DepositorService.class);
            SubmissionRepo submissionRepo = mock(SubmissionRepo.class);
            PackagerUtility packagerUtility = mock(PackagerUtility.class);
            SubmissionEmailService submissionEmailService = mock(SubmissionEmailService.class);
            BatchDepositSummaryEmailService summaryEmailService = mock(BatchDepositSummaryEmailService.class);
            Depositor depositor = mock(Depositor.class);
            DepositLocation location = mock(DepositLocation.class);
            Packager packager = mock(Packager.class);
            User user = mock(User.class);
            SubmissionStatus published = mock(SubmissionStatus.class);

            lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(published);
            lenient().when(locationRepo.findById(DEPOSIT_LOCATION_ID)).thenReturn(Optional.of(location));
            lenient().when(location.getDepositorName()).thenReturn("depositor");
            lenient().when(location.getPackager()).thenReturn(packager);
            lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
            lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
            lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());

            // Real submissions carrying prior state: status, deposit URL, and action log.
            List<Submission> submissions = new ArrayList<>(size);
            Map<Submission, ExportPackage> packages = new IdentityHashMap<>();
            Map<ExportPackage, Integer> indexByPackage = new IdentityHashMap<>();
            SubmissionStatus[] priorStatus = new SubmissionStatus[size];
            String[] priorUrl = new String[size];
            Set[] priorLogs = new Set[size];
            boolean[] depositFails = new boolean[size];
            boolean[] statusUpdateFails = new boolean[size];
            boolean[] emailFails = new boolean[size];
            RuntimeException[] depositErrors = new RuntimeException[size];
            for (int i = 0; i < size; i++) {
                Submission submission = new Submission();
                ReflectionTestUtils.setField(submission, "id", 1L + i);
                priorStatus[i] = mock(SubmissionStatus.class);
                priorUrl[i] = gen.random().nextBoolean() ? null : "http://prior.example.org/" + i;
                submission.setSubmissionStatus(priorStatus[i]);
                submission.setDepositURL(priorUrl[i]);
                Set<ActionLog> logs = new HashSet<>();
                int logCount = gen.random().nextInt(3);
                for (int l = 0; l < logCount; l++) {
                    logs.add(mock(ActionLog.class));
                }
                submission.setActionLogs(logs);
                priorLogs[i] = new HashSet<>(logs);

                int mode = gen.random().nextInt(3);
                depositFails[i] = mode == 1;
                statusUpdateFails[i] = mode == 2;
                emailFails[i] = gen.random().nextBoolean();
                depositErrors[i] = new RuntimeException("deposit failed " + i, new IllegalStateException("root " + i));

                ExportPackage exportPackage = mock(ExportPackage.class);
                submissions.add(submission);
                packages.put(submission, exportPackage);
                indexByPackage.put(exportPackage, i);

                if (emailFails[i]) {
                    doThrow(new RuntimeException("email failed " + i)).when(submissionEmailService).sendWorkflowEmails(eq(user), eq(1L + i));
                }
            }
            lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
            lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenAnswer(inv -> packages.get(inv.getArgument(1)));
            lenient().when(depositor.deposit(any(), any())).thenAnswer(inv -> {
                int index = indexByPackage.get(inv.<ExportPackage>getArgument(1));
                if (depositFails[index]) {
                    throw depositErrors[index];
                }
                return "http://deposited.example.org/" + index;
            });
            // Models the repository: status changes on success, throws for the failing-after-URL case.
            lenient().when(submissionRepo.updateStatus(any(Submission.class), any(), any())).thenAnswer(inv -> {
                Submission s = inv.getArgument(0);
                int index = (int) (s.getId() - 1L);
                if (statusUpdateFails[index]) {
                    throw new IllegalStateException("status update failed " + index);
                }
                s.setSubmissionStatus(inv.getArgument(1));
                return s;
            });

            BatchDepositService service = new BatchDepositService();
            ReflectionTestUtils.setField(service, "submissionStatusRepo", statusRepo);
            ReflectionTestUtils.setField(service, "depositLocationRepo", locationRepo);
            ReflectionTestUtils.setField(service, "depositorService", depositorService);
            ReflectionTestUtils.setField(service, "submissionRepo", submissionRepo);
            ReflectionTestUtils.setField(service, "packagerUtility", packagerUtility);
            ReflectionTestUtils.setField(service, "submissionEmailService", submissionEmailService);
            ReflectionTestUtils.setField(service, "batchDepositSummaryEmailService", summaryEmailService);

            BatchDepositResult result = service.publish(user, DEPOSIT_LOCATION_ID);

            List<DepositOutcome> outcomes = result.getOutcomes();
            assertEquals(size, outcomes.size(), "outcome count" + ctx);

            for (int i = 0; i < size; i++) {
                Submission submission = submissions.get(i);
                DepositOutcome outcome = outcomes.get(i);
                String sctx = " submission " + i + ctx;
                assertEquals(1L + i, outcome.getSubmissionId(), "outcome id" + sctx);

                if (depositFails[i] || statusUpdateFails[i]) {
                    // Failed: status, URL, and action log as before; exception retained.
                    assertFalse(outcome.isDeposited(), "failed outcome" + sctx);
                    assertSame(priorStatus[i], submission.getSubmissionStatus(), "status unchanged" + sctx);
                    assertEquals(priorUrl[i], submission.getDepositURL(), "deposit URL unchanged" + sctx);
                    assertEquals(priorLogs[i], submission.getActionLogs(), "action log unchanged" + sctx);
                    assertNotNull(outcome.getCause(), "exception retained" + sctx);
                    if (depositFails[i]) {
                        assertSame(depositErrors[i], outcome.getCause(), "deposit exception retained" + sctx);
                    }
                } else {
                    // Deposited: URL from depositor, status Published, even if workflow email failed.
                    assertTrue(outcome.isDeposited(), "deposited outcome (emailFails=" + emailFails[i] + ")" + sctx);
                    assertEquals("http://deposited.example.org/" + i, submission.getDepositURL(), "deposit URL set" + sctx);
                    assertSame(published, submission.getSubmissionStatus(), "status Published" + sctx);
                    assertNull(outcome.getCause(), "no exception on deposited" + sctx);
                }
            }
        });
    }

}
