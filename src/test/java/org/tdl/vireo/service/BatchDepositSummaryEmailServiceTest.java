package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.tdl.vireo.model.DepositLocation;
import org.tdl.vireo.model.EmailTemplate;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.batch.DepositOutcome;
import org.tdl.vireo.model.repo.EmailTemplateRepo;
import org.tdl.vireo.utility.TemplateUtility;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit tests for {@link BatchDepositSummaryEmailService}.
 */
@ExtendWith(MockitoExtension.class)
public class BatchDepositSummaryEmailServiceTest {

    private static final String TEMPLATE_NAME = BatchDepositSummaryEmailService.TEMPLATE_NAME;

    @Mock
    private EmailTemplateRepo emailTemplateRepo;

    @Spy
    private TemplateUtility templateUtility = new TemplateUtility();

    @Mock
    private VireoEmailSender emailSender;

    @InjectMocks
    private BatchDepositSummaryEmailService service;

    private ListAppender<ILoggingEvent> appender;

    private Logger serviceLogger;

    private final ZonedDateTime finishedAt = ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneId.of("UTC"));

    private final List<DepositOutcome> outcomes = Arrays.asList(
        DepositOutcome.deposited(1, "First"),
        DepositOutcome.deposited(2, "Second"),
        DepositOutcome.failed(3, "Third", "boom", new RuntimeException("boom")));

    @BeforeEach
    public void attachAppender() {
        serviceLogger = (Logger) LoggerFactory.getLogger(BatchDepositSummaryEmailService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.setLevel(Level.ALL);
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    public void detachAppender() {
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

    private List<ILoggingEvent> logs(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).collect(Collectors.toList());
    }

    private User reviewer(String email, String preferred) {
        User user = mock(User.class);
        when(user.getSetting("preferedEmail")).thenReturn(preferred);
        if (preferred == null) {
            when(user.getEmail()).thenReturn(email);
        }
        lenient(user);
        return user;
    }

    private static void lenient(User user) {
        org.mockito.Mockito.lenient().when(user.getName()).thenReturn("Rita Reviewer");
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(42L);
    }

    private DepositLocation location(String name) {
        DepositLocation location = mock(DepositLocation.class);
        org.mockito.Mockito.lenient().when(location.getName()).thenReturn(name);
        return location;
    }

    private EmailTemplate template(String subject, String message) {
        EmailTemplate template = mock(EmailTemplate.class);
        when(template.getSubject()).thenReturn(subject);
        when(template.getMessage()).thenReturn(message);
        return template;
    }

    @Test
    public void testCustomTemplatePreferredOverSystemTemplate() throws Exception {
        doReturn(template("custom subject", "custom body")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, false);

        service.send(reviewer("rita@example.org", null), location("Repo"), outcomes, finishedAt);

        verify(emailSender).sendEmail("rita@example.org", "custom subject", "custom body");
        verify(emailTemplateRepo, never()).findByNameAndSystemRequired(TEMPLATE_NAME, true);
    }

    @Test
    public void testSystemTemplateUsedWhenNoCustomExists() throws Exception {
        when(emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, false)).thenReturn(null);
        doReturn(template("system subject", "system body")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, true);

        service.send(reviewer("rita@example.org", null), location("Repo"), outcomes, finishedAt);

        verify(emailSender).sendEmail("rita@example.org", "system subject", "system body");
    }

    @Test
    public void testPreferredEmailSettingUsedAsAddress() throws Exception {
        doReturn(template("s", "b")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, false);

        service.send(reviewer("ignored@example.org", "preferred@example.org"), location("Repo"), outcomes, finishedAt);

        verify(emailSender).sendEmail("preferred@example.org", "s", "b");
    }

    @Test
    public void testBlankAddressLogsOneWarnAndSkipsSender() throws Exception {
        service.send(reviewer("   ", null), location("Repo"), outcomes, finishedAt);

        verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString());
        List<ILoggingEvent> warns = logs(Level.WARN);
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).getFormattedMessage().contains("Rita Reviewer"));
        assertEquals(0, logs(Level.ERROR).size());
    }

    @Test
    public void testMissingTemplateLogsOneErrorNamingTemplate() throws Exception {
        when(emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, false)).thenReturn(null);
        when(emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true)).thenReturn(null);

        service.send(reviewer("rita@example.org", null), location("Repo"), outcomes, finishedAt);

        verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString());
        List<ILoggingEvent> errors = logs(Level.ERROR);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getFormattedMessage().contains(TEMPLATE_NAME));
        assertEquals(0, logs(Level.WARN).size());
    }

    @Test
    public void testMessagingExceptionLogsOneErrorWithCountsAndDoesNotThrow() throws Exception {
        doReturn(template("s", "b")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, false);
        doThrow(new MessagingException("smtp down")).when(emailSender).sendEmail(anyString(), anyString(), anyString());

        service.send(reviewer("rita@example.org", null), location("Repo"), outcomes, finishedAt);

        assertSendFailureLogged();
    }

    @Test
    public void testMailExceptionLogsOneErrorWithCountsAndDoesNotThrow() throws Exception {
        doReturn(template("s", "b")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, false);
        doThrow(new MailSendException("smtp down")).when(emailSender).sendEmail(anyString(), anyString(), anyString());

        service.send(reviewer("rita@example.org", null), location("Repo"), outcomes, finishedAt);

        assertSendFailureLogged();
    }

    private void assertSendFailureLogged() {
        List<ILoggingEvent> errors = logs(Level.ERROR);
        assertEquals(1, errors.size());
        String message = errors.get(0).getFormattedMessage();
        assertTrue(message.contains("Rita Reviewer"), message);
        assertTrue(message.contains("Deposited: 2"), message);
        assertTrue(message.contains("Failed: 1"), message);
    }

    @Test
    public void testDollarAndBackslashValuesAppearLiterallyInSentBody() throws Exception {
        String tricky = "Cost $1 and $2 \\n C:\\temp\\$0 {FAILED_LIST}";
        doReturn(template("To {DEPOSIT_LOCATION}", "Location: {DEPOSIT_LOCATION}\n{FAILED_LIST}")).when(emailTemplateRepo).findByNameAndSystemRequired(TEMPLATE_NAME, false);
        List<DepositOutcome> trickyOutcomes = Arrays.asList(
            DepositOutcome.failed(7, tricky, "reason $1 \\ end", new RuntimeException("x")));

        service.send(reviewer("rita@example.org", null), location(tricky), trickyOutcomes, finishedAt);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendEmail(anyString(), subject.capture(), body.capture());
        assertEquals("To " + tricky, subject.getValue());
        assertTrue(body.getValue().contains("Location: " + tricky), body.getValue());
        assertTrue(body.getValue().contains("- #7 " + tricky + ": reason $1 \\ end"), body.getValue());
        assertEquals(0, logs(Level.ERROR).size());
    }

}
