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
        "DEPOSIT_LOCATION", "TOTAL_COUNT", "DEPOSITED_COUNT", "FAILED_COUNT", "NOT_RECORDED_COUNT",
        "FINISHED_AT", "NOT_RECORDED_LIST", "DEPOSITED_LIST", "FAILED_LIST");

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

    private String uncommentedResource() throws Exception {
        return Arrays.stream(readResource().split("\\r?\\n"))
            .filter(l -> !l.startsWith("#"))
            .collect(Collectors.joining("\n"));
    }

    @Test
    public void testResourceUsesEveryParameterWithNotRecordedFirst() throws Exception {
        String uncommented = uncommentedResource();

        assertTrue(uncommented.contains("Subject: Batch Publish Summary: {DEPOSITED_COUNT} deposited, {FAILED_COUNT} failed, {NOT_RECORDED_COUNT} not recorded"), uncommented);
        for (String parameter : PARAMETERS) {
            assertTrue(uncommented.contains("{" + parameter + "}"), "Subject or body must use {" + parameter + "}");
        }
        int notRecorded = uncommented.indexOf("{NOT_RECORDED_LIST}");
        assertTrue(notRecorded < uncommented.indexOf("{FAILED_LIST}") && notRecorded < uncommented.indexOf("{DEPOSITED_LIST}"),
            "Not recorded section must come before the failed and deposited lists");
    }

    @Test
    public void testLoaderInstallsTemplateAsSystemRequired() throws Exception {
        EmailTemplate template = emailTemplateRepo.findByNameAndSystemRequired(TEMPLATE_NAME, true);

        assertNotNull(template, "Loader must install " + TEMPLATE_NAME);
        assertTrue(template.getSystemRequired(), "Template must be systemRequired");
        // The loader strips any line containing '#'; the installed text must still have the new section.
        assertTrue(template.getMessage().contains("{NOT_RECORDED_LIST}"), template.getMessage());
        assertTrue(template.getMessage().contains("Publishing them again creates duplicates."), template.getMessage());
        assertTrue(template.getSubject().contains("{NOT_RECORDED_COUNT} not recorded"), template.getSubject());
    }

    @Test
    public void testSecondRunLeavesEditedTemplateUnchanged() throws Exception {
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