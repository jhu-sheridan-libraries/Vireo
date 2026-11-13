package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.mock.BatchTestGenerators;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.FieldValue;
import org.tdl.vireo.model.NamedSearchFilterGroup;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionListColumn;
import org.tdl.vireo.model.SubmissionStatus;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.BatchDepositResult;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.FailureReason;
import org.tdl.vireo.model.depositor.Depositor;
import org.tdl.vireo.model.packager.Packager;
import org.tdl.vireo.model.repo.DepositLocationRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;

/**
 * Feature: batch-deposit-summary-email, Property 4: Outcome title handling
 *
 * **Validates: Requirements 2.5**
 */
public class BatchDepositServiceOutcomeTitleTest {

    private static final long BASE_SEED = 20240604L;

    private static final int ITERATIONS = 100;

    private static final long DEPOSIT_LOCATION_ID = 7L;

    private static final int BATCH_SIZE = 5;

    /** Through the service: failing deposits with generated titles. */
    @Test
    public void failedOutcomeTitleIsAbsentExactlyWhenNullOrBlank() {
        // Feature: batch-deposit-summary-email, Property 4: Outcome title handling
        BatchTestGenerators.runIterations(BASE_SEED, ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + BASE_SEED + ", iteration=" + iteration + "]";

            SubmissionStatusRepo statusRepo = mock(SubmissionStatusRepo.class);
            DepositLocationRepo locationRepo = mock(DepositLocationRepo.class);
            DepositorService depositorService = mock(DepositorService.class);
            SubmissionRepo submissionRepo = mock(SubmissionRepo.class);
            PackagerUtility packagerUtility = mock(PackagerUtility.class);
            SubmissionEmailService submissionEmailService = mock(SubmissionEmailService.class);
            BatchDepositSummaryEmailService summaryEmailService = mock(BatchDepositSummaryEmailService.class);
            Depositor depositor = mock(Depositor.class);
            DepositLocation location = mock(DepositLocation.class);
            User user = mock(User.class);

            lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(mock(SubmissionStatus.class));
            lenient().when(locationRepo.findById(DEPOSIT_LOCATION_ID)).thenReturn(Optional.of(location));
            lenient().when(location.getDepositorName()).thenReturn("depositor");
            lenient().when(location.getPackager()).thenReturn(mock(Packager.class));
            lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
            lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
            lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());

            List<Submission> submissions = new ArrayList<>();
            String[] titles = new String[BATCH_SIZE];
            String[] messages = new String[BATCH_SIZE];
            for (int i = 0; i < BATCH_SIZE; i++) {
                titles[i] = gen.title();
                messages[i] = gen.nonBlankText();
                Submission submission = mock(Submission.class);
                lenient().when(submission.getId()).thenReturn(100L + i);
                if (titles[i] == null) {
                    // A submission without a title field value yields an empty title.
                    lenient().when(submission.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.emptyList());
                } else {
                    FieldValue fieldValue = mock(FieldValue.class);
                    lenient().when(fieldValue.getValue()).thenReturn(titles[i]);
                    lenient().when(submission.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.singletonList(fieldValue));
                }
                submissions.add(submission);
            }
            lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
            lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenAnswer(inv -> {
                Submission s = inv.getArgument(1);
                throw new IllegalStateException(messages[(int) (s.getId() - 100L)]);
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
            assertEquals(BATCH_SIZE, outcomes.size(), "outcome count" + ctx);
            for (int i = 0; i < BATCH_SIZE; i++) {
                assertFailedOutcome(outcomes.get(i), 100L + i, titles[i] == null ? "" : titles[i], messages[i], ctx + " submission " + i);
            }
        });
    }

    /** Through DepositOutcome construction: null, empty, blank, and text titles. */
    @Test
    public void failedOutcomeConstructionTitleHandling() {
        // Feature: batch-deposit-summary-email, Property 4: Outcome title handling
        BatchTestGenerators.runIterations(BASE_SEED + 1, ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + (BASE_SEED + 1) + ", iteration=" + iteration + "]";
            long id = gen.random().nextInt(Integer.MAX_VALUE);
            String title = gen.title();
            Throwable cause = gen.throwableChain();
            String reason = FailureReason.of(cause);

            DepositOutcome outcome = DepositOutcome.failed(id, title, reason, cause);

            assertFailedOutcome(outcome, id, title, reason, ctx);
            assertSame(cause, outcome.getCause(), "cause retained" + ctx);
        });
    }

    private static void assertFailedOutcome(DepositOutcome outcome, long id, String title, String reason, String ctx) {
        assertFalse(outcome.isDeposited(), "failed outcome" + ctx);
        assertEquals(id, outcome.getSubmissionId(), "submission id" + ctx);
        assertEquals(reason, outcome.getFailureReason(), "failure reason" + ctx);
        assertNotNull(outcome.getFailureReason(), "reason present" + ctx);
        boolean absent = title == null || title.trim().isEmpty();
        if (absent) {
            assertNull(outcome.getTitle(), "title absent for " + (title == null ? "null" : "'" + title + "'") + ctx);
        } else {
            assertEquals(title, outcome.getTitle(), "title equals input" + ctx);
        }
    }

}
