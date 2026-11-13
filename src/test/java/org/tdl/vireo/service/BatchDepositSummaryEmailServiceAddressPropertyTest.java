package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.tdl.vireo.mock.BatchTestGenerators;
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
 * Randomized property test for the reviewer address selection of
 * {@link BatchDepositSummaryEmailService}.
 */
public class BatchDepositSummaryEmailServiceAddressPropertyTest {

    private static final long BASE_SEED = 20240611L;

    private static final String TEMPLATE_NAME = BatchDepositSummaryEmailService.TEMPLATE_NAME;

    // Feature: batch-deposit-summary-email, Property 11: Exactly one email to the selected address, or none
    // Validates: Requirements 3.1, 5.3
    @Test
    public void exactlyOneEmailToSelectedAddressOrNone() {
        BatchTestGenerators.runIterations(BASE_SEED, 150, (gen, iteration) -> {
            String where = "seed=" + BASE_SEED + ", iteration=" + iteration + ": ";
            Random random = gen.random();

            // Setting: absent (null) or present; email: any. Present values may be blank.
            String setting = maybeAddress(gen, random, true);
            String email = maybeAddress(gen, random, false);
            String selected = setting != null ? setting : email;
            boolean expectSend = selected != null && !selected.trim().isEmpty();

            EmailTemplateRepo repo = mock(EmailTemplateRepo.class);
            EmailTemplate template = mock(EmailTemplate.class);
            when(template.getSubject()).thenReturn("subject");
            when(template.getMessage()).thenReturn("message");
            when(repo.findByNameAndSystemRequired(TEMPLATE_NAME, false)).thenReturn(template);
            VireoEmailSender sender = mock(VireoEmailSender.class);

            User user = mock(User.class);
            when(user.getSetting("preferedEmail")).thenReturn(setting);
            when(user.getEmail()).thenReturn(email);
            when(user.getName()).thenReturn("Reviewer");
            when(user.getId()).thenReturn(7L);
            DepositLocation location = mock(DepositLocation.class);
            when(location.getName()).thenReturn("Repo");

            BatchDepositSummaryEmailService service = new BatchDepositSummaryEmailService();
            inject(service, "emailTemplateRepo", repo);
            inject(service, "templateUtility", new TemplateUtility());
            inject(service, "emailSender", sender);

            List<DepositOutcome> outcomes = gen.outcomes(random.nextInt(6));

            Logger logger = (Logger) LoggerFactory.getLogger(BatchDepositSummaryEmailService.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            Level previous = logger.getLevel();
            logger.setLevel(Level.ALL);
            logger.addAppender(appender);
            try {
                service.send(user, location, outcomes, gen.zonedDateTime());

                if (expectSend) {
                    try {
                        verify(sender, times(1)).sendEmail(anyString(), anyString(), anyString());
                        verify(sender).sendEmail(selected, "subject", "message");
                    } catch (Exception e) {
                        throw new AssertionError(where + "sender verification failed: " + e.getMessage(), e);
                    }
                    assertEquals(0, count(appender, Level.WARN), where + "no WARN expected for address '" + selected + "'");
                } else {
                    try {
                        verify(sender, never()).sendEmail(anyString(), anyString(), anyString());
                    } catch (Exception e) {
                        throw new AssertionError(where + "sender verification failed: " + e.getMessage(), e);
                    }
                    assertEquals(1, count(appender, Level.WARN), where + "exactly one WARN expected for selected address '" + selected + "'");
                }
                assertEquals(0, count(appender, Level.ERROR), where + "no ERROR expected");
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(previous);
                appender.stop();
            }
        });
    }

    /** Null, empty, whitespace, or a valid-looking address. */
    private static String maybeAddress(BatchTestGenerators gen, Random random, boolean nullMoreLikely) {
        int pick = random.nextInt(nullMoreLikely ? 6 : 5);
        switch (pick) {
        case 0:
            return null;
        case 1:
            return "";
        case 2:
            return gen.blank();
        default:
            return "user" + random.nextInt(100000) + "@example.org";
        }
    }

    private static long count(ListAppender<ILoggingEvent> appender, Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).collect(Collectors.counting());
    }

    private static void inject(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

}
