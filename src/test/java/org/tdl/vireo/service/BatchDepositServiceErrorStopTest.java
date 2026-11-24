package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
import org.tdl.vireo.model.batch.BatchDepositSummaryBuilder;
import org.tdl.vireo.model.batch.BatchStop;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.SummaryEmailResult;
import org.tdl.vireo.model.depositor.Depositor;
import org.tdl.vireo.model.export.ExportPackage;
import org.tdl.vireo.model.packager.Packager;
import org.tdl.vireo.model.repo.DepositLocationRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStatusRepo;
import org.tdl.vireo.utility.PackagerUtility;

/**
 * An Error on one submission stops the batch: that submission is recorded as usual, the rest are
 * reported as not attempted, the summary is still sent, and the same Error is rethrown.
 */
public class BatchDepositServiceErrorStopTest {

    private static final long DEPOSIT_LOCATION_ID = 7L;

    private SubmissionRepo submissionRepo;

    private PackagerUtility packagerUtility;

    private SubmissionEmailService submissionEmailService;

    private BatchDepositSummaryEmailService summaryEmailService;

    private Depositor depositor;

    private User user;

    private BatchDepositService service;

    private List<Submission> submissions;

    @BeforeEach
    public void setUp() throws Exception {
        SubmissionStatusRepo statusRepo = mock(SubmissionStatusRepo.class);
        DepositLocationRepo locationRepo = mock(DepositLocationRepo.class);
        DepositorService depositorService = mock(DepositorService.class);
        submissionRepo = mock(SubmissionRepo.class);
        packagerUtility = mock(PackagerUtility.class);
        submissionEmailService = mock(SubmissionEmailService.class);
        summaryEmailService = mock(BatchDepositSummaryEmailService.class);
        depositor = mock(Depositor.class);
        DepositLocation location = mock(DepositLocation.class);
        user = mock(User.class);

        lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(mock(SubmissionStatus.class));
        lenient().when(locationRepo.findById(DEPOSIT_LOCATION_ID)).thenReturn(Optional.of(location));
        lenient().when(location.getDepositorName()).thenReturn("depositor");
        lenient().when(location.getPackager()).thenReturn(mock(Packager.class));
        lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
        lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
        lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());

        submissions = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            submissions.add(submission(id, "Title " + id));
        }
        lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
        lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenReturn(mock(ExportPackage.class));
        lenient().when(depositor.deposit(any(DepositLocation.class), any(ExportPackage.class))).thenReturn("http://repo.example.org/handle/x");
        lenient().when(submissionRepo.updateStatus(any(Submission.class), any(SubmissionStatus.class), any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new BatchDepositService();
        ReflectionTestUtils.setField(service, "submissionStatusRepo", statusRepo);
        ReflectionTestUtils.setField(service, "depositLocationRepo", locationRepo);
        ReflectionTestUtils.setField(service, "depositorService", depositorService);
        ReflectionTestUtils.setField(service, "submissionRepo", submissionRepo);
        ReflectionTestUtils.setField(service, "packagerUtility", packagerUtility);
        ReflectionTestUtils.setField(service, "submissionEmailService", submissionEmailService);
        ReflectionTestUtils.setField(service, "batchDepositSummaryEmailService", summaryEmailService);
    }

    private static Submission submission(long id, String title) {
        Submission submission = mock(Submission.class);
        lenient().when(submission.getId()).thenReturn(id);
        FieldValue value = mock(FieldValue.class);
        lenient().when(value.getValue()).thenReturn(title);
        lenient().when(submission.getFieldValuesByPredicateValue("dc.title")).thenReturn(Collections.singletonList(value));
        return submission;
    }

    @Test
    public void errorWhilePackagingStopsBatchAndIsRethrown() {
        OutOfMemoryError error = new OutOfMemoryError("Java heap space");
        lenient().when(packagerUtility.packageExport(any(Packager.class), eq(submissions.get(1)))).thenThrow(error);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 2L, 1);

        assertTrue(outcomes.get(0).isDeposited());
        assertTrue(outcomes.get(1).isFailed());
        assertEquals("Java heap space", outcomes.get(1).getFailureReason());
        assertSame(error, outcomes.get(1).getCause());
        assertNotAttempted(outcomes.get(2), 3L, "Title 3", 2L);
        verify(packagerUtility, never()).packageExport(any(Packager.class), eq(submissions.get(2)));
    }

    @Test
    public void errorWhileDepositingIsFailed() throws Exception {
        StackOverflowError error = new StackOverflowError();
        ExportPackage second = mock(ExportPackage.class);
        lenient().when(packagerUtility.packageExport(any(Packager.class), eq(submissions.get(1)))).thenReturn(second);
        lenient().when(depositor.deposit(any(DepositLocation.class), eq(second))).thenThrow(error);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 2L, 1);

        assertTrue(outcomes.get(1).isFailed());
        assertEquals("StackOverflowError", outcomes.get(1).getFailureReason());
        assertNotAttempted(outcomes.get(2), 3L, "Title 3", 2L);
    }

    @Test
    public void errorAfterRemoteDepositIsNotRecorded() {
        OutOfMemoryError error = new OutOfMemoryError("Java heap space");
        lenient().when(submissionRepo.updateStatus(eq(submissions.get(1)), any(SubmissionStatus.class), any(User.class))).thenThrow(error);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 2L, 1);

        assertTrue(outcomes.get(1).isNotRecorded());
        assertEquals("http://repo.example.org/handle/x", outcomes.get(1).getDepositURL());
        verify(submissionEmailService, never()).sendWorkflowEmails(user, 2L);
        assertNotAttempted(outcomes.get(2), 3L, "Title 3", 2L);
    }

    @Test
    public void errorInWorkflowEmailsKeepsDepositedAndStops() {
        NoClassDefFoundError error = new NoClassDefFoundError("javax/mail/Foo");
        doThrow(error).when(submissionEmailService).sendWorkflowEmails(user, 2L);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 2L, 1);

        assertTrue(outcomes.get(1).isDeposited());
        assertNull(outcomes.get(1).getCause());
        assertNotAttempted(outcomes.get(2), 3L, "Title 3", 2L);
    }

    @Test
    public void errorWhileReadingTitleIsFailedWithoutTitle() {
        OutOfMemoryError error = new OutOfMemoryError("Java heap space");
        lenient().when(submissions.get(1).getFieldValuesByPredicateValue("dc.title")).thenThrow(error);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 2L, 1);

        assertTrue(outcomes.get(1).isFailed());
        assertNull(outcomes.get(1).getTitle());
        verify(packagerUtility, never()).packageExport(any(Packager.class), eq(submissions.get(1)));
    }

    @Test
    public void errorOnLastSubmissionLeavesNothingUnattempted() {
        OutOfMemoryError error = new OutOfMemoryError("Java heap space");
        lenient().when(packagerUtility.packageExport(any(Packager.class), eq(submissions.get(2)))).thenThrow(error);

        List<DepositOutcome> outcomes = publishExpectingStop(error, 3L, 0);

        assertTrue(outcomes.get(0).isDeposited());
        assertTrue(outcomes.get(1).isDeposited());
        assertTrue(outcomes.get(2).isFailed());
    }

    @Test
    public void originalErrorSurvivesAnErrorWhileSendingSummary() {
        OutOfMemoryError error = new OutOfMemoryError("first");
        OutOfMemoryError sendError = new OutOfMemoryError("second");
        lenient().when(packagerUtility.packageExport(any(Packager.class), eq(submissions.get(0)))).thenThrow(error);
        doThrow(sendError).when(summaryEmailService).send(any(), any(), any(), any(), any(BatchStop.class));

        Error thrown = assertThrows(Error.class, () -> service.publish(user, DEPOSIT_LOCATION_ID));

        assertSame(error, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(sendError, thrown.getSuppressed()[0]);
    }

    @Test
    public void normalRunKeepsTheSummaryEmailResult() {
        org.mockito.Mockito.when(summaryEmailService.send(any(), any(), any(), any())).thenReturn(SummaryEmailResult.NO_TEMPLATE);

        assertEquals(SummaryEmailResult.NO_TEMPLATE, service.publish(user, DEPOSIT_LOCATION_ID).getSummaryEmail());
    }

    @Test
    public void normalRunUsesPlainSummaryAndDoesNotStop() {
        service.publish(user, DEPOSIT_LOCATION_ID);

        verify(summaryEmailService).send(any(), any(), any(), any());
        verify(summaryEmailService, never()).send(any(), any(), any(), any(), any());
        verify(packagerUtility, times(3)).packageExport(any(Packager.class), any(Submission.class));
    }

    @SuppressWarnings("unchecked")
    private List<DepositOutcome> publishExpectingStop(Error expected, long stoppedOn, int notAttempted) {
        Error thrown = assertThrows(Error.class, () -> service.publish(user, DEPOSIT_LOCATION_ID));
        assertSame(expected, thrown, "the same Error is rethrown");

        ArgumentCaptor<List<DepositOutcome>> outcomes = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BatchStop> stop = ArgumentCaptor.forClass(BatchStop.class);
        verify(summaryEmailService).send(any(), any(), outcomes.capture(), any(), stop.capture());
        verify(summaryEmailService, never()).send(any(), any(), any(), any());
        assertEquals(stoppedOn, stop.getValue().getSubmissionId());
        assertEquals(notAttempted, stop.getValue().getNotAttempted());
        assertEquals(submissions.size(), outcomes.getValue().size(), "every matched submission has an outcome");
        // Nothing after the stop is recorded or emailed.
        verify(submissionRepo, never()).updateStatus(eq(submissions.get(submissions.size() - 1)), any(SubmissionStatus.class), any(User.class));
        verify(submissionEmailService, never()).sendWorkflowEmails(user, (long) submissions.size());
        return outcomes.getValue();
    }

    private static void assertNotAttempted(DepositOutcome outcome, long id, String title, long stoppedOn) {
        assertTrue(outcome.isFailed(), "not attempted is reported as failed");
        assertEquals(id, outcome.getSubmissionId());
        assertEquals(title, outcome.getTitle());
        assertEquals(BatchDepositSummaryBuilder.notAttemptedReason(stoppedOn), outcome.getFailureReason());
        assertNull(outcome.getCause());
    }

}
