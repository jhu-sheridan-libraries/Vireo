package org.tdl.vireo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.tdl.vireo.exception.BatchExportException;
import org.tdl.vireo.mock.BatchTestGenerators;
import org.tdl.vireo.model.Action;
import org.tdl.vireo.model.batch.BatchDepositResult;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.batch.SummaryEmailResult;
import org.tdl.vireo.service.BatchDepositService;
import org.tdl.vireo.model.ActionLog;
import org.tdl.vireo.model.NamedSearchFilterGroup;
import org.tdl.vireo.model.Role;
import org.tdl.vireo.model.SubmissionState;
import org.tdl.vireo.model.SubmissionStatus;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.repo.ActionLogRepo;
import org.tdl.vireo.utility.PackagerUtility;

import edu.tamu.weaver.response.ApiResponse;
import edu.tamu.weaver.response.ApiStatus;

@ActiveProfiles(value = { "test", "isolated-test" })
public class SubmissionControllerTest extends AbstractControllerTest {

    private static final String TEST_USER_1_EMAIL = "User 1 email";
    private static final String TEST_USER_1_FIRST_NAME = "User 1 first name";
    private static final String TEST_USER_1_LAST_NAME = "User 1 last name";
    private static final String TEST_HASH = "97a74588d324c59d8d8fbdd5187292d7";
    private static final Role TEST_USER_1_ROLE = Role.ROLE_ADMIN;

    private static final User TEST_USER_1 = new User(TEST_USER_1_EMAIL, TEST_USER_1_FIRST_NAME, TEST_USER_1_LAST_NAME, TEST_USER_1_ROLE);

    private static final Calendar TEST_CALENDAR_1 = GregorianCalendar.getInstance();
    private static final Calendar TEST_CALENDAR_2 = GregorianCalendar.getInstance();
    private static final Calendar TEST_CALENDAR_3 = GregorianCalendar.getInstance();

    private static final SubmissionStatus TEST_SUBMISSION_STATUS_1 = new SubmissionStatus("In Progress", false, false, false, true, true, true, SubmissionState.IN_PROGRESS);
    private static final SubmissionStatus TEST_SUBMISSION_STATUS_2 = new SubmissionStatus("Needs Corrections", false, false, false, true, true, true, SubmissionState.NEEDS_CORRECTIONS);
    private static final SubmissionStatus TEST_SUBMISSION_STATUS_3 = new SubmissionStatus("Approved", false, false, false, true, true, true, SubmissionState.APPROVED);

    private static final ActionLog TEST_ACTION_LOG_1 = new ActionLog(Action.UNDETERMINED, TEST_SUBMISSION_STATUS_1, TEST_CALENDAR_1, "Action log entrty 1", false);
    private static final ActionLog TEST_ACTION_LOG_2 = new ActionLog(Action.UNDETERMINED, TEST_SUBMISSION_STATUS_2, TEST_CALENDAR_2, "Action log entrty 2", true);
    private static final ActionLog TEST_ACTION_LOG_3 = new ActionLog(Action.UNDETERMINED, TEST_SUBMISSION_STATUS_3, TEST_CALENDAR_3, "Action log entrty 3", false);

    private static final List<ActionLog> TEST_ACTION_LOG_LIST = new ArrayList<>(Arrays.asList(TEST_ACTION_LOG_1, TEST_ACTION_LOG_2, TEST_ACTION_LOG_3));
    private static final List<ActionLog> TEST_ACTION_LOG_LIST_PAGE_ASSIGNED = new ArrayList<>(Arrays.asList(TEST_ACTION_LOG_1));

    private static final Page<ActionLog> TEST_ACTION_LOG_PAGE = new PageImpl<>(TEST_ACTION_LOG_LIST);

    @Mock
    private ActionLogRepo actionLogRepo;

    @Mock
    private NamedSearchFilterGroup activeFilter;

    @Mock
    private PackagerUtility packagerUtility;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream outputStream;

    @Mock
    private BatchDepositService batchDepositService;

    @InjectMocks
    private SubmissionController submissionController;

    private static final long BATCH_BASE_SEED = 20240612L;
    private static final int BATCH_ITERATIONS = 100;

    @BeforeEach
    public void setup() {

    }

    @Test
    public void testGetActionLogs() {
        when(actionLogRepo.getAllActionLogs(any(Long.class), any(), any(Pageable.class))).thenReturn(TEST_ACTION_LOG_PAGE);

        Pageable pageable = PageRequest.of(0, TEST_ACTION_LOG_LIST_PAGE_ASSIGNED.size());
        ApiResponse response = submissionController.getActionLogs(TEST_USER_1, 1L, pageable);

        assertEquals(ApiStatus.SUCCESS, response.getMeta().getStatus());
        assertEquals(pageable.getPageSize(), response.getPayload().size());
    }

    @Test
    public void testGetActionLogsForAdvisement() {
        when(actionLogRepo.getAllActionLogs(any(String.class), any(), any(Pageable.class))).thenReturn(TEST_ACTION_LOG_PAGE);

        Pageable pageable = PageRequest.of(0, TEST_ACTION_LOG_LIST_PAGE_ASSIGNED.size());
        ApiResponse response = submissionController.getActionLogsForAdvisement(TEST_HASH, pageable);

        assertEquals(ApiStatus.SUCCESS, response.getMeta().getStatus());
        assertEquals(pageable.getPageSize(), response.getPayload().size());
    }

    @Test
    public void testBatchPublishPreconditionFailureReturnsError() {
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(BatchDepositResult.preconditionFailed("No deposit location"));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals(ApiStatus.ERROR, result.getMeta().getStatus());
        assertEquals("No deposit location", result.getMeta().getMessage());
        verify(batchDepositService).publish(TEST_USER_1, 7L);
    }

    @Test
    public void testBatchPublishEmptyBatchIsSuccessWithZeroCounts() {
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(new ArrayList<DepositOutcome>()));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals(ApiStatus.SUCCESS, result.getMeta().getStatus());
        Map<String, Object> counts = counts(result);
        assertEquals(0, counts.get("deposited"));
        assertEquals(0, counts.get("failed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBatchPublishResponseStatusFollowsFailureCount() {
        // Feature: batch-deposit-summary-email, Property 12: Response status follows failure count
        // Validates: Requirements 6.1, 6.2
        BatchTestGenerators.runIterations(BATCH_BASE_SEED, BATCH_ITERATIONS, (gen, iteration) -> {
            String ctx = " [seed=" + BATCH_BASE_SEED + ", iteration=" + iteration + "]";
            int size = gen.sizeForIteration(iteration);
            List<DepositOutcome> outcomes = gen.outcomes(size);
            // Force the no-failure branch for a share of iterations so both branches are well covered.
            if (iteration % 3 == 0) {
                List<DepositOutcome> allDeposited = new ArrayList<>();
                for (DepositOutcome o : outcomes) {
                    allDeposited.add(DepositOutcome.deposited(o.getSubmissionId(), o.getTitle()));
                }
                outcomes = allDeposited;
            }
            BatchDepositResult batch = new BatchDepositResult(outcomes);
            int d = batch.deposited();
            int f = batch.failed();

            when(batchDepositService.publish(TEST_USER_1, 9L)).thenReturn(batch);

            ApiResponse result = submissionController.batchPublish(TEST_USER_1, 9L);
            Map<String, Object> counts = counts(result);

            if (f == 0) {
                assertEquals(ApiStatus.SUCCESS, result.getMeta().getStatus(), "status for d=" + d + ", f=" + f + ctx);
            } else {
                assertEquals(ApiStatus.ERROR, result.getMeta().getStatus(), "status for d=" + d + ", f=" + f + ctx);
                String message = result.getMeta().getMessage();
                assertTrue(message != null && !message.isEmpty(), "message present" + ctx);
                assertTrue(message.contains(String.valueOf(d)) && message.contains(String.valueOf(f)), "message has counts d=" + d + ", f=" + f + ": " + message + ctx);
            }
            assertEquals(d, counts.get("deposited"), "deposited count" + ctx);
            assertEquals(f, counts.get("failed"), "failed count" + ctx);
        });
    }

    @Test
    public void testBatchPublishNotRecordedIsErrorWithSeparateCount() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        outcomes.add(DepositOutcome.deposited(1L, "One"));
        outcomes.add(DepositOutcome.notRecorded(2L, "Two", "http://repo.example.org/2", "log failed", new RuntimeException("log failed")));
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(outcomes));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals(ApiStatus.ERROR, result.getMeta().getStatus());
        assertEquals("Batch publish finished: 1 deposited, 0 failed, 1 deposited but not recorded in Vireo (do not retry these). See the summary email for details.",
            result.getMeta().getMessage());
        Map<String, Object> counts = counts(result);
        assertEquals(1, counts.get("deposited"));
        assertEquals(0, counts.get("failed"));
        assertEquals(1, counts.get("notRecorded"));
    }

    @Test
    public void testBatchPublishMessageOmitsNotRecordedWhenZero() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        outcomes.add(DepositOutcome.failed(1L, "One", "boom", new RuntimeException("boom")));
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(outcomes));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals("Batch publish finished: 0 deposited, 1 failed. See the summary email for details.", result.getMeta().getMessage());
        assertEquals(0, counts(result).get("notRecorded"));
    }

    private List<DepositOutcome> oneOfEach() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        outcomes.add(DepositOutcome.deposited(1L, "One"));
        outcomes.add(DepositOutcome.failed(4L, "Four", "boom", new RuntimeException("boom")));
        outcomes.add(DepositOutcome.notRecorded(3L, "Three", "http://repo.example.org/3", "log failed", new RuntimeException("log failed")));
        outcomes.add(DepositOutcome.failed(7L, "Seven", "boom", new RuntimeException("boom")));
        return outcomes;
    }

    @Test
    public void testBatchPublishMessageWhenSummaryTemplateIsMissing() {
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(oneOfEach(), SummaryEmailResult.NO_TEMPLATE));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals(ApiStatus.ERROR, result.getMeta().getStatus());
        assertEquals("Batch publish finished: 1 deposited, 2 failed, 1 deposited but not recorded in Vireo (do not retry these)."
            + " No summary email was sent because the email template 'SYSTEM Batch Deposit Summary' is missing."
            + " Not recorded: #3. Failed: #4, #7. Ask an administrator to check the server log for details.",
            result.getMeta().getMessage());
    }

    @Test
    public void testBatchPublishMessageNamesEachNotSentReason() {
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(oneOfEach(), SummaryEmailResult.NO_ADDRESS));
        assertTrue(submissionController.batchPublish(TEST_USER_1, 7L).getMeta().getMessage()
            .contains(" No summary email was sent because your account has no email address. Not recorded: #3."));

        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(oneOfEach(), SummaryEmailResult.SEND_FAILED));
        assertTrue(submissionController.batchPublish(TEST_USER_1, 7L).getMeta().getMessage()
            .contains(" No summary email was sent because sending the email failed. Not recorded: #3."));
    }

    @Test
    public void testBatchPublishMessageKeepsSummaryEmailTextWhenSent() {
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(oneOfEach(), SummaryEmailResult.SENT));

        String message = submissionController.batchPublish(TEST_USER_1, 7L).getMeta().getMessage();

        assertTrue(message.endsWith(" See the summary email for details."), message);
        assertTrue(!message.contains("#3"), message);
    }

    @Test
    public void testBatchPublishIdsListNotRecordedFirstAndStopAtTen() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            outcomes.add(DepositOutcome.notRecorded(id, "NR", "http://repo/" + id, "r", null));
        }
        for (long id = 101; id <= 112; id++) {
            outcomes.add(DepositOutcome.failed(id, "F", "r", null));
        }

        assertEquals(" Not recorded: #1, #2, #3. Failed: #101, #102, #103, #104, #105, #106, #107, and 5 more.",
            SubmissionController.batchPublishIds(outcomes));
    }

    @Test
    public void testBatchPublishIdsWhenNotRecordedUseTheWholeLimit() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            outcomes.add(DepositOutcome.notRecorded(id, "NR", "http://repo/" + id, "r", null));
        }
        outcomes.add(DepositOutcome.failed(101L, "F", "r", null));
        outcomes.add(DepositOutcome.failed(102L, "F", "r", null));
        outcomes.add(DepositOutcome.deposited(200L, "D"));

        assertEquals(" Not recorded: #1, #2, #3, #4, #5, #6, #7, #8, #9, #10, and 2 more. Failed: 2 not listed.",
            SubmissionController.batchPublishIds(outcomes));
    }

    @Test
    public void testBatchPublishSuccessIgnoresUnsentSummary() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        outcomes.add(DepositOutcome.deposited(1L, "One"));
        when(batchDepositService.publish(TEST_USER_1, 7L)).thenReturn(new BatchDepositResult(outcomes, SummaryEmailResult.NO_TEMPLATE));

        ApiResponse result = submissionController.batchPublish(TEST_USER_1, 7L);

        assertEquals(ApiStatus.SUCCESS, result.getMeta().getStatus());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> counts(ApiResponse result) {
        Object payload = result.getPayload().values().iterator().next();
        return (Map<String, Object>) payload;
    }

    @Test
    void testBatchExportErrorMessage() {
        TEST_USER_1.setActiveFilter(activeFilter);
        when(packagerUtility.getPackager(anyString())).thenReturn(null);

        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        assertThrows(BatchExportException.class, () -> {
            submissionController.batchExport(mockResponse, TEST_USER_1, "INVALID-PACKAGER-NAME");
        });
    }
}
