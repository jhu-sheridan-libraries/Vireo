package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Example-based unit tests for {@link BatchDepositService}.
 *
 * Covers Requirements 1.1, 1.2, 2.4, 5.1, 5.2, 7.1 to 7.5, and 8.1 to 8.3.
 */
public class BatchDepositServiceTest {

    private static final long LOCATION_ID = 7L;

    private static final String REVIEWER_ADDRESS = "reviewer@example.org";

    private SubmissionStatusRepo statusRepo;
    private DepositLocationRepo locationRepo;
    private DepositorService depositorService;
    private SubmissionRepo submissionRepo;
    private PackagerUtility packagerUtility;
    private SubmissionEmailService submissionEmailService;
    private EmailTemplateRepo emailTemplateRepo;
    private VireoEmailSender emailSender;
    private Depositor depositor;
    private DepositLocation location;
    private Packager packager;
    private User user;
    private SubmissionStatus published;

    private BatchDepositService service;

    private ListAppender<ILoggingEvent> appender;
    private Logger serviceLogger;

    /** The level the logger had before this class raised it to ALL, restored so other test classes keep the configured level. */
    private static Level originalServiceLoggerLevel;

    @BeforeAll
    public static void rememberServiceLoggerLevel() {
        originalServiceLoggerLevel = ((Logger) LoggerFactory.getLogger(BatchDepositService.class)).getLevel();
    }

    @AfterAll
    public static void restoreServiceLoggerLevel() {
        ((Logger) LoggerFactory.getLogger(BatchDepositService.class)).setLevel(originalServiceLoggerLevel);
    }

    /** Index of each export package, so the depositor can fail by submission position. */
    private final Map<ExportPackage, Integer> indexByPackage = new IdentityHashMap<>();
    private final Map<Submission, ExportPackage> packages = new IdentityHashMap<>();

    @BeforeEach
    public void setUp() {
        if (appender != null) {
            // setUp is re-run inside multi-run tests; drop the previous appender.
            tearDown();
        }
        indexByPackage.clear();
        packages.clear();
        statusRepo = mock(SubmissionStatusRepo.class);
        locationRepo = mock(DepositLocationRepo.class);
        depositorService = mock(DepositorService.class);
        submissionRepo = mock(SubmissionRepo.class);
        packagerUtility = mock(PackagerUtility.class);
        submissionEmailService = mock(SubmissionEmailService.class);
        emailTemplateRepo = mock(EmailTemplateRepo.class);
        emailSender = mock(VireoEmailSender.class);
        depositor = mock(Depositor.class);
        location = mock(DepositLocation.class);
        packager = mock(Packager.class);
        user = mock(User.class);
        published = mock(SubmissionStatus.class);

        lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(published);
        lenient().when(locationRepo.findById(LOCATION_ID)).thenReturn(Optional.of(location));
        lenient().when(location.getName()).thenReturn("Repository");
        lenient().when(location.getDepositorName()).thenReturn("depositor");
        lenient().when(location.getPackager()).thenReturn(packager);
        lenient().when(depositorService.getDepositor("depositor")).thenReturn(depositor);
        lenient().when(user.getActiveFilter()).thenReturn(mock(NamedSearchFilterGroup.class));
        lenient().when(user.getSubmissionViewColumns()).thenReturn(new ArrayList<SubmissionListColumn>());
        lenient().when(user.getName()).thenReturn("Rita Reviewer");
        lenient().when(user.getId()).thenReturn(42L);
        lenient().when(user.getSetting("preferedEmail")).thenReturn(null);
        lenient().when(user.getEmail()).thenReturn(REVIEWER_ADDRESS);

        EmailTemplate template = mock(EmailTemplate.class);
        lenient().when(template.getSubject()).thenReturn("Summary");
        lenient().when(template.getMessage()).thenReturn("total={TOTAL_COUNT};deposited={DEPOSITED_COUNT};failed={FAILED_COUNT}\n{FAILED_LIST}");
        lenient().when(emailTemplateRepo.findByNameAndSystemRequired(BatchDepositSummaryEmailService.TEMPLATE_NAME, false)).thenReturn(null);
        lenient().when(emailTemplateRepo.findByNameAndSystemRequired(BatchDepositSummaryEmailService.TEMPLATE_NAME, true)).thenReturn(template);

        lenient().when(packagerUtility.packageExport(any(Packager.class), any(Submission.class))).thenAnswer(inv -> packages.get(inv.getArgument(1)));
        lenient().when(submissionRepo.updateStatus(any(Submission.class), any(), any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setSubmissionStatus(inv.getArgument(1));
            return s;
        });

        BatchDepositSummaryEmailService summaryEmailService = new BatchDepositSummaryEmailService();
        ReflectionTestUtils.setField(summaryEmailService, "emailTemplateRepo", emailTemplateRepo);
        ReflectionTestUtils.setField(summaryEmailService, "templateUtility", new TemplateUtility());
        ReflectionTestUtils.setField(summaryEmailService, "emailSender", emailSender);

        service = new BatchDepositService();
        ReflectionTestUtils.setField(service, "submissionStatusRepo", statusRepo);
        ReflectionTestUtils.setField(service, "depositLocationRepo", locationRepo);
        ReflectionTestUtils.setField(service, "depositorService", depositorService);
        ReflectionTestUtils.setField(service, "submissionRepo", submissionRepo);
        ReflectionTestUtils.setField(service, "packagerUtility", packagerUtility);
        ReflectionTestUtils.setField(service, "submissionEmailService", submissionEmailService);
        ReflectionTestUtils.setField(service, "batchDepositSummaryEmailService", summaryEmailService);

        serviceLogger = (Logger) LoggerFactory.getLogger(BatchDepositService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.setLevel(Level.ALL);
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() {
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

    /**
     * Prepares a batch of the given size. Submission i (0-based) has id i + 1 and fails
     * in the depositor when failing[i] is true.
     */
    private List<Submission> prepareBatch(boolean... failing) {
        List<Submission> submissions = new ArrayList<>();
        for (int i = 0; i < failing.length; i++) {
            Submission submission = new Submission();
            ReflectionTestUtils.setField(submission, "id", 1L + i);
            submission.setSubmissionStatus(mock(SubmissionStatus.class));
            submission.setDepositURL("http://prior.example.org/" + i);
            ExportPackage exportPackage = mock(ExportPackage.class);
            submissions.add(submission);
            packages.put(submission, exportPackage);
            indexByPackage.put(exportPackage, i);
        }
        lenient().when(submissionRepo.batchDynamicSubmissionQuery(any(), any())).thenReturn(submissions);
        lenient().when(depositor.deposit(any(), any())).thenAnswer(inv -> {
            int index = indexByPackage.get(inv.<ExportPackage>getArgument(1));
            if (failing[index]) {
                throw new RuntimeException("deposit failed " + index);
            }
            return "http://deposited.example.org/" + index;
        });
        return submissions;
    }

    private String capturedBody() throws MessagingException {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(1)).sendEmail(eq(REVIEWER_ADDRESS), anyString(), body.capture());
        return body.getValue();
    }

    private void assertCounts(BatchDepositResult result, int deposited, int failed) throws MessagingException {
        int total = deposited + failed;
        assertFalse(result.isPreconditionFailed());
        assertEquals(total, result.total());
        assertEquals(deposited, result.deposited());
        assertEquals(failed, result.failed());
        assertTrue(capturedBody().startsWith("total=" + total + ";deposited=" + deposited + ";failed=" + failed + "\n"), "email counts");
    }

    private List<ILoggingEvent> logs(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).collect(Collectors.toList());
    }

    private static String state(List<Submission> submissions, SubmissionStatus published) {
        StringBuilder sb = new StringBuilder();
        for (Submission s : submissions) {
            sb.append(s.getId()).append('|').append(s.getSubmissionStatus() == published ? "published" : "prior").append('|').append(s.getDepositURL()).append(';');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- Req 8.1

    @Test
    public void allSucceed() throws Exception {
        List<Submission> submissions = prepareBatch(false, false, false, false);

        BatchDepositResult result = service.publish(user, LOCATION_ID);

        assertCounts(result, 4, 0);
        for (Submission s : submissions) {
            assertSame(published, s.getSubmissionStatus());
            assertEquals("http://deposited.example.org/" + (s.getId() - 1), s.getDepositURL());
        }
        verify(submissionEmailService, times(4)).sendWorkflowEmails(any(), any(Long.class));
        assertTrue(logs(Level.ERROR).isEmpty());
    }

    @Test
    public void someFail() throws Exception {
        List<Submission> submissions = prepareBatch(false, true, false, true, false);

        BatchDepositResult result = service.publish(user, LOCATION_ID);

        assertCounts(result, 3, 2);
        List<DepositOutcome> outcomes = result.getOutcomes();
        assertTrue(outcomes.get(0).isDeposited());
        assertFalse(outcomes.get(1).isDeposited());
        assertEquals("deposit failed 1", outcomes.get(1).getFailureReason());
        assertFalse(outcomes.get(3).isDeposited());
        // Failed submissions are untouched.
        assertEquals("http://prior.example.org/1", submissions.get(1).getDepositURL());
        assertTrue(submissions.get(1).getSubmissionStatus() != published);
        assertSame(published, submissions.get(2).getSubmissionStatus());
        assertTrue(capturedBody().contains("#2") && capturedBody().contains("#4") && !capturedBody().contains("#3"), "failed list in email");
        verify(submissionEmailService, times(3)).sendWorkflowEmails(any(), any(Long.class));
    }

    @Test
    public void allFail() throws Exception {
        List<Submission> submissions = prepareBatch(true, true, true);

        BatchDepositResult result = service.publish(user, LOCATION_ID);

        assertCounts(result, 0, 3);
        for (Submission s : submissions) {
            assertTrue(s.getSubmissionStatus() != published);
        }
        verify(submissionEmailService, never()).sendWorkflowEmails(any(), any(Long.class));
    }

    // ------------------------------------------------------------- Req 8.2

    @Test
    public void firstFailsAndEveryFollowingSubmissionIsStillAttempted() throws Exception {
        List<Submission> submissions = prepareBatch(true, false, false, false);

        BatchDepositResult result = service.publish(user, LOCATION_ID);

        assertCounts(result, 3, 1);
        assertFalse(result.getOutcomes().get(0).isDeposited());
        verify(depositor, times(4)).deposit(any(), any());
        for (int i = 1; i < submissions.size(); i++) {
            assertSame(published, submissions.get(i).getSubmissionStatus());
            verify(packagerUtility, times(1)).packageExport(packager, submissions.get(i));
        }
    }

    // ------------------------------------------------------------- Req 8.3

    private String runWithSender(boolean[] failing, Throwable senderFailure, List<String> outcomesOut) throws Exception {
        setUp();
        List<Submission> submissions = prepareBatch(failing);
        if (senderFailure instanceof MessagingException) {
            doThrow(senderFailure).when(emailSender).sendEmail(anyString(), anyString(), anyString());
        } else if (senderFailure != null) {
            doThrow((RuntimeException) senderFailure).when(emailSender).sendEmail(anyString(), anyString(), anyString());
        }
        BatchDepositResult[] holder = new BatchDepositResult[1];
        assertDoesNotThrow(() -> holder[0] = service.publish(user, LOCATION_ID));
        verify(emailSender, times(1)).sendEmail(anyString(), anyString(), anyString());
        for (DepositOutcome o : holder[0].getOutcomes()) {
            outcomesOut.add(o.getSubmissionId() + "|" + o.isDeposited() + "|" + o.getFailureReason());
        }
        outcomesOut.add("counts=" + holder[0].deposited() + "/" + holder[0].failed() + "/" + holder[0].total());
        verify(depositor, times(failing.length)).deposit(any(), any());
        verify(submissionRepo, times(holder[0].deposited())).updateStatus(any(Submission.class), any(), any());
        return state(submissions, published);
    }

    @Test
    public void senderThrowingDoesNotChangeResultOrPersistedCalls() throws Exception {
        boolean[] failing = { false, true, false, true };

        List<String> baselineOutcomes = new ArrayList<>();
        String baselineState = runWithSender(failing, null, baselineOutcomes);

        Throwable[] failures = { new MessagingException("smtp down"), new MailSendException("mail failure") };
        for (Throwable failure : failures) {
            List<String> outcomes = new ArrayList<>();
            String state = runWithSender(failing, failure, outcomes);
            assertEquals(baselineOutcomes, outcomes, "outcomes with " + failure.getClass().getSimpleName());
            assertEquals(baselineState, state, "persisted state with " + failure.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------- Req 7

    @Test
    public void preconditionCasesReportFirstMissingInOrderWithNoSideEffects() throws Exception {
        for (int mask = 1; mask < 8; mask++) {
            setUp();
            prepareBatch(false, false, false);
            boolean noStatus = (mask & 1) != 0;
            boolean noLocation = (mask & 2) != 0;
            boolean noDepositor = (mask & 4) != 0;
            if (noStatus) {
                lenient().when(statusRepo.findByName(BatchDepositService.PUBLISHED_STATUS_NAME)).thenReturn(null);
            }
            if (noLocation) {
                lenient().when(locationRepo.findById(LOCATION_ID)).thenReturn(Optional.empty());
            }
            if (noDepositor) {
                lenient().when(depositorService.getDepositor("depositor")).thenReturn(null);
            }

            BatchDepositResult result = service.publish(user, LOCATION_ID);

            String ctx = "mask=" + mask;
            assertTrue(result.isPreconditionFailed(), ctx);
            String message = result.getPreconditionError();
            assertNotNull(message, ctx);
            if (noStatus) {
                assertTrue(message.contains(BatchDepositService.PUBLISHED_STATUS_NAME), ctx + ": " + message);
            } else if (noLocation) {
                assertTrue(message.contains("deposit location") && message.contains(String.valueOf(LOCATION_ID)), ctx + ": " + message);
            } else {
                assertTrue(message.contains("depositor"), ctx + ": " + message);
            }
            verifyNoInteractions(depositor);
            verifyNoInteractions(packagerUtility);
            verifyNoInteractions(submissionEmailService);
            verifyNoInteractions(emailSender);
            verify(submissionRepo, never()).batchDynamicSubmissionQuery(any(), any());
            verify(submissionRepo, never()).updateStatus(any(Submission.class), any(), any());
        }
    }

    // ------------------------------------------------------------- Req 2.4

    @Test
    public void oneErrorLogPerFailedSubmissionWithThrowableAttached() {
        prepareBatch(false, true, true, false, true);

        service.publish(user, LOCATION_ID);

        List<ILoggingEvent> errors = logs(Level.ERROR);
        assertEquals(3, errors.size());
        String[] expectedIds = { "2", "3", "5" };
        for (int i = 0; i < errors.size(); i++) {
            ILoggingEvent event = errors.get(i);
            assertNotNull(event.getThrowableProxy(), "throwable attached");
            assertEquals(RuntimeException.class.getName(), event.getThrowableProxy().getClassName());
            assertEquals("deposit failed " + (Integer.parseInt(expectedIds[i]) - 1), event.getThrowableProxy().getMessage());
            assertEquals(expectedIds[i], String.valueOf(event.getArgumentArray()[0]));
        }
        assertNull(logs(Level.WARN).stream().findAny().orElse(null));
    }

}
