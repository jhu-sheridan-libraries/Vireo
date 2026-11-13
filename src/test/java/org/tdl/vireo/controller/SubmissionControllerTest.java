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
