package org.tdl.vireo.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Verifies the batch deposit summary email feature stays free of
 * institution-specific content so it can be contributed upstream.
 *
 * The denylist lives only in this test. This file is not among the scanned files.
 *
 * Validates: Requirements 8.5
 */
class BatchDepositUpstreamNeutralityTest {

    // Matched case-insensitively as substrings.
    private static final List<String> DENYLIST = List.of(
        "jhu",
        "library.jhu.edu",
        "johns hopkins",
        "hopkins",
        "vireo-stage",
        "vireo-prod");

    private static final List<String> SCANNED_FILES = List.of(
        "src/main/resources/emails/SYSTEM_Batch_Deposit_Summary.email",
        "src/main/java/org/tdl/vireo/service/BatchDepositService.java",
        "src/main/java/org/tdl/vireo/service/BatchDepositSummaryEmailService.java",
        "src/main/java/org/tdl/vireo/model/batch/BatchDepositSummaryBuilder.java",
        "src/main/java/org/tdl/vireo/model/batch/SummaryContent.java",
        "src/main/java/org/tdl/vireo/model/batch/FailureReason.java",
        "src/main/java/org/tdl/vireo/model/batch/DepositOutcome.java",
        "src/main/java/org/tdl/vireo/model/batch/BatchDepositResult.java",
        // Contains the templateLiteral addition.
        "src/main/java/org/tdl/vireo/utility/TemplateUtility.java");

    @Test
    void newTemplateAndSourceFilesContainNoInstitutionSpecificTokens() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String relative : SCANNED_FILES) {
            Path path = Paths.get(relative);
            assertTrue(Files.isRegularFile(path), "Expected file to scan not found: " + path.toAbsolutePath());

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String lower = lines.get(i).toLowerCase(Locale.ROOT);
                for (String token : DENYLIST) {
                    if (lower.contains(token)) {
                        violations.add(relative + ":" + (i + 1) + " contains denied token '" + token + "'");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "Institution-specific content found:\n" + String.join("\n", violations));
    }
}
