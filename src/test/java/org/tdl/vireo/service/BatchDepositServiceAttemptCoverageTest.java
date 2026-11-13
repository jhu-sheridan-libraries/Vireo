package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.mock.BatchTestGenerators;
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
 * Feature: batch-deposit-summary-email, Property 1: Every matched submission is attempted exactly once
 *
 * **Validates: Requirements 1.1, 1.2, 1.7, 1.8**
 */
public class BatchDepositServiceAttemptCoverageTest {

    private static final long BASE_SEED = 20240601L;

    private static final int ITERATIONS = 100;

    private static final long DEPOSIT_LOCATION_ID = 7L;

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void everyMatchedSubmissionIsAttemptedExactlyOnce() {
        BatchTestGenerators.runIterations(BASE_SEED, ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + BASE_SEED + ", iteration=" + iteration + "]";
            int size = gen.sizeForIteration(iteration);

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

            // Build matched submissions, each with its own package and a random success/failure.
            List<Submission> submissions = new ArrayList<>(size);
            Map<Submission, ExportPackage> packages = new IdentityHashMap<>();
            Map<ExportPackage, Integer> indexByPackage = new IdentityHashMap<>();
            boolean[] shouldFail = new boolean[size];
            for (int i = 0; i < size; i++) {
                Submission submission = mock(Submission.class);
                lenient().when(submission.getId()).thenReturn(1L + i);
                ExportPackage exportPackage = mock(ExportPackage.class);
                submissions.add(submission);
                packages.put(submission, exportPackage);
                indexByPackage.put(exportPackage, i);
                shouldFail[i] = gen.random().nextBoolean();
            }
            lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
            lenient().when(submissionRepo.updateStatus(any(Submission.class), any(), any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenAnswer(inv -> packages.get(inv.getArgument(1)));

            int[] attempts = new int[size];
            List<Integer> attemptOrder = new ArrayList<>();
            lenient().when(depositor.deposit(any(), any())).thenAnswer(inv -> {
                int index = indexByPackage.get(inv.<ExportPackage>getArgument(1));
                attempts[index]++;
                attemptOrder.add(index);
                if (shouldFail[index]) {
                    throw new RuntimeException("deposit failed " + index);
                }
                return "http://example.org/" + index;
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

            assertFalse(result.isPreconditionFailed(), "precondition must pass" + ctx);
            assertNull(result.getPreconditionError(), "no precondition error" + ctx);

            // Exactly one attempt per submission, in result order, including after failures.
            for (int i = 0; i < size; i++) {
                assertEquals(1, attempts[i], "submission " + i + " attempt count" + ctx);
            }
            assertEquals(size, attemptOrder.size(), "total attempts" + ctx);
            for (int i = 0; i < size; i++) {
                assertEquals(i, attemptOrder.get(i).intValue(), "attempt order at position " + i + ctx);
            }

            // Outcomes cover the matched list in order and sum to its size.
            List<DepositOutcome> outcomes = result.getOutcomes();
            assertEquals(size, outcomes.size(), "outcome count equals query size" + ctx);
            int expectedFailed = 0;
            for (int i = 0; i < size; i++) {
                if (shouldFail[i]) {
                    expectedFailed++;
                }
                assertEquals(!shouldFail[i], outcomes.get(i).isDeposited(), "outcome status at position " + i + ctx);
            }
            assertEquals(size, result.deposited() + result.failed(), "deposited + failed equals size" + ctx);
            assertEquals(expectedFailed, result.failed(), "failed count" + ctx);
            assertEquals(size - expectedFailed, result.deposited(), "deposited count" + ctx);
            if (size == 0) {
                assertEquals(0, result.deposited(), "zero deposited for empty list" + ctx);
                assertEquals(0, result.failed(), "zero failed for empty list" + ctx);
            }
        });
    }

}
