package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tdl.vireo.Application;
import org.tdl.vireo.model.EmailTemplate;
import org.tdl.vireo.model.repo.EmailTemplateRepo;

/**
 * Tests the Batch Deposit Summary email template resource and its installation
 * by {@link SystemDataLoader}.
 *
 * Requirements 4.1, 4.2, 4.3, 4.4, 4.5.
 */
@ActiveProfiles(value = { "test", "isolated-test" })
@SpringBootTest(classes = { Application.class })
@Transactional(propagation = Propagation.REQUIRES_NEW)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@TestInstance(Lifecycle.PER_CLASS)
public class BatchDepositSummaryTemplateTest {

    private static final String TEMPLATE_NAME = "SYSTEM Batch Deposit Summary";

    private static final String RESOURCE_PATH = "/emails/SYSTEM_Batch_Deposit_Summary.email";

    private static final List<String> PARAMETERS = Arrays.asList(
        "DEPOSIT_LOCATION", "TOTAL_COUNT", "DEPOSITED_COUNT", "FAILED_COUNT",
        "FINISHED_AT", "DEPOSITED_LIST", "FAILED_LIST");

    @Autowired
    private SystemDataLoader systemDataLoader;

    @Autowired
    private EmailTemplateRepo emailTemplateRepo;

    /**
     * Runs the first load once for the whole class. It runs outside the per-test transaction, so it
     * is committed and every test starts from the loaded state. The @DirtiesContext on this class
     * discards that state when the class finishes. Each test then only performs the loads it is
     * actually about (the second run).
     */
    @BeforeAll
    public void loadSystemDataOnce() throws Exception {
        systemDataLoader.loadSystemData();
    }

    private String readResource() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Template resource " + RESOURCE_PATH + " must exist on the classpath");
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void testResourceDocumentsAllParameters() throws Exception {
        List<String> lines = Arrays.asList(readResource().split("\\r?\\n"));
        List<String> comments = lines.stream().filter(l -> l.startsWith("#")).collect(Collectors.toList());

        for (String parameter : PARAMETERS) {
            assertTrue(comments.stream().anyMatch(c -> c.contains("{" + parameter + "}")),
                "Header comment must document {" + parameter + "}");
        }
    }

    @Test
    public void testResourceUsesAllParametersInSubjectOrBody() throws Exception {
        String content = readResource();
        String uncommented = Arrays.stream(content.split("\\r?\\n"))
            .filter(l -> !l.startsWith("#"))
            .collect(Collectors.joining("\n"));

        assertTrue(uncommented.contains("Subject:"), "Template must define a subject");

        for (String parameter : PARAMETERS) {
            assertTrue(uncommented.contains("{" + parameter + "}"),
                "Subject or body must use {" + parameter + "}");
        }
    }

    @Test
    public void testLoaderInstallsTemplateAsSystemRequired() throws Exception {
        // The load itself happened once in loadSystemDataOnce().
        EmailTemplate template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);

        assertNotNull(template, "Loader must install " + TEMPLATE_NAME);
        assertEquals(TEMPLATE_NAME, template.getName());
        assertTrue(template.getSystemRequired(), "Template must be systemRequired");
        assertTrue(template.getSubject() != null && !template.getSubject().trim().isEmpty(), "Subject must not be empty");
        assertTrue(template.getMessage() != null && !template.getMessage().trim().isEmpty(), "Message must not be empty");
    }

    @Test
    public void testSecondRunCreatesNoDuplicate() throws Exception {
        // The first run was loadSystemDataOnce(); the load below is the second run.
        long totalAfterFirst = emailTemplateRepo.count();
        String subject = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true).getSubject();
        String message = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true).getMessage();

        systemDataLoader.loadSystemData();

        assertEquals(1, emailTemplateRepo.findByName(TEMPLATE_NAME).size(), "Second run must not duplicate the template");
        assertEquals(totalAfterFirst, emailTemplateRepo.count(), "Second run must not change the number of templates");
        EmailTemplate template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);
        assertEquals(subject, template.getSubject());
        assertEquals(message, template.getMessage());
    }

    @Test
    public void testSecondRunLeavesEditedTemplateUnchanged() throws Exception {
        // The first run was loadSystemDataOnce(); the load below is the second run.

        // An administrator edit is stored as a custom (non system required) copy.
        EmailTemplate custom = emailTemplateRepo.create(TEMPLATE_NAME, "Edited subject", "Edited message");
        custom.setSystemRequired(false);
        custom = emailTemplateRepo.save(custom);
        Long customId = custom.getId();

        long totalBefore = emailTemplateRepo.count();
        EmailTemplate systemBefore = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);
        String systemSubject = systemBefore.getSubject();
        String systemMessage = systemBefore.getMessage();

        systemDataLoader.loadSystemData();

        assertEquals(totalBefore, emailTemplateRepo.count(), "Second run must not create a duplicate");
        assertEquals(2, emailTemplateRepo.findByName(TEMPLATE_NAME).size(), "Only the system and the edited copy must exist");

        EmailTemplate customAfter = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, false);
        assertEquals(customId, customAfter.getId());
        assertEquals("Edited subject", customAfter.getSubject());
        assertEquals("Edited message", customAfter.getMessage());

        EmailTemplate systemAfter = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);
        assertTrue(systemAfter.getSystemRequired());
        assertEquals(systemSubject, systemAfter.getSubject());
        assertEquals(systemMessage, systemAfter.getMessage());
    }

}
