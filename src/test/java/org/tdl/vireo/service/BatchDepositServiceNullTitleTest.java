package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.FieldValue;
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
 * A submission whose title cannot be read must not abort the batch or block its own deposit.
 */
public class BatchDepositServiceNullTitleTest {

    private static final long DEPOSIT_LOCATION_ID = 7L;

    @Test
    public void storedNullTitleValueDoesNotAbortBatch() {
        // dc.title field value exists but its value is null (FieldValue.value is nullable).
        Submission nullTitle = submission(1L);
        FieldValue nullValue = mock(FieldValue.class);
        lenient().when(nullValue.getValue()).thenReturn(null);
        lenient().when(nullTitle.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.singletonList(nullValue));

        Submission titled = submission(2L);
        FieldValue value = mock(FieldValue.class);
        lenient().when(value.getValue()).thenReturn("A Title");
        lenient().when(titled.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.singletonList(value));

        List<DepositOutcome> outcomes = run(List.of(nullTitle, titled), summaryCapture());

        assertEquals(2, outcomes.size());
        assertTrue(outcomes.get(0).isDeposited(), "null-title submission is still deposited");
        assertNull(outcomes.get(0).getTitle());
        assertTrue(outcomes.get(1).isDeposited(), "later submission is still attempted");
        assertEquals("A Title", outcomes.get(1).getTitle());
    }

    @Test
    public void unreadableTitleDoesNotAbortBatch() {
        Submission broken = submission(1L);
        lenient().when(broken.getFieldValuesByPredicateValue("dc.title")).thenThrow(new IllegalStateException("lazy load failed"));

        Submission other = submission(2L);
        lenient().when(other.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.emptyList());

        List<DepositOutcome> outcomes = run(List.of(broken, other), summaryCapture());

        assertEquals(2, outcomes.size());
        assertTrue(outcomes.get(0).isDeposited());
        assertNull(outcomes.get(0).getTitle());
        assertTrue(outcomes.get(1).isDeposited());
    }

    private static BatchDepositSummaryEmailService summaryCapture() {
        return mock(BatchDepositSummaryEmailService.class);
    }

    private static Submission submission(long id) {
        Submission submission = mock(Submission.class);
        lenient().when(submission.getId()).thenReturn(id);
        return submission;
    }

    @SuppressWarnings("unchecked")
    private static List<DepositOutcome> run(List<Submission> submissions, BatchDepositSummaryEmailService summaryEmailService) {
        SubmissionStatusRepo statusRepo = mock(SubmissionStatusRepo.class);
        DepositLocationRepo locationRepo = mock(DepositLocationRepo.class);
        DepositorService depositorService = mock(DepositorService.class);
        SubmissionRepo submissionRepo = mock(SubmissionRepo.class);
        PackagerUtility packagerUtility = mock(PackagerUtility.class);
        SubmissionEmailService submissionEmailService = mock(SubmissionEmailService.class);
        Depositor depositor = mock(Depositor.class);
        DepositLocation location = mock(DepositLocation.class);
        User user = mock(User.class);
        SubmissionStatus published = mock(SubmissionStatus.class);

        lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(published);
        lenient().when(locationRepo.findById(DEPOSIT_LOCATION_ID)).thenReturn(Optional.of(location));
        lenient().when(location.getDepositorName()).thenReturn("depositor");
        lenient().when(location.getPackager()).thenReturn(mock(Packager.class));
        lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
        lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
        lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());
        lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
        lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenReturn(mock(ExportPackage.class));
        try {
            lenient().when(depositor.deposit(any(DepositLocation.class), any(ExportPackage.class))).thenReturn("http://example.org/handle/1");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        lenient().when(submissionRepo.updateStatus(any(Submission.class), any(SubmissionStatus.class), any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDepositService service = new BatchDepositService();
        ReflectionTestUtils.setField(service, "submissionStatusRepo", statusRepo);
        ReflectionTestUtils.setField(service, "depositLocationRepo", locationRepo);
        ReflectionTestUtils.setField(service, "depositorService", depositorService);
        ReflectionTestUtils.setField(service, "submissionRepo", submissionRepo);
        ReflectionTestUtils.setField(service, "packagerUtility", packagerUtility);
        ReflectionTestUtils.setField(service, "submissionEmailService", submissionEmailService);
        ReflectionTestUtils.setField(service, "batchDepositSummaryEmailService", summaryEmailService);

        BatchDepositResult result = service.publish(user, DEPOSIT_LOCATION_ID);

        // The summary email is still sent after the loop.
        ArgumentCaptor<List<DepositOutcome>> captor = ArgumentCaptor.forClass(List.class);
        verify(summaryEmailService).send(any(), any(), captor.capture(), any());
        assertEquals(result.getOutcomes().size(), captor.getValue().size());

        return result.getOutcomes();
    }
}
