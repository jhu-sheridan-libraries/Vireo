package org.tdl.vireo.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Randomized test for TemplateUtility.templateLiteral.
 *
 * Feature: batch-deposit-summary-email, Property 9: Template substitution is literal
 *
 * Validates: Requirements 4.8
 */
class TemplateUtilityLiteralSubstitutionTest {

    private static final long BASE_SEED = 20240609L;

    private static final int ITERATIONS = 200;

    private static final List<String> PLACEHOLDERS = Arrays.asList(
        "DEPOSIT_LOCATION", "TOTAL_COUNT", "DEPOSITED_COUNT", "FAILED_COUNT",
        "FINISHED_AT", "DEPOSITED_LIST", "FAILED_LIST");

    // Fragments that are hostile to naive regex replacement or rescanning.
    private static final String[] NASTY_FRAGMENTS = {
        "$", "$1", "${x}", "\\", "\\\\", "\\$", "{", "}", "{}", "{ }", "{lower}",
        "{FAILED_LIST}", "{DEPOSIT_LOCATION}", "{TOTAL_COUNT}", "{UNKNOWN_TOKEN}",
        "\n", "\r\n", "\t", " ", "- #12 title: reason", "(", ")", "[", "]", ".", "*", "+", "?", "^", "|"
    };

    private final TemplateUtility templateUtility = new TemplateUtility();

    @Test
    @DisplayName("Property 9: template substitution inserts values literally")
    void substitutionIsLiteral() {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            long seed = BASE_SEED * 31 + iteration;
            String context = "seed=" + seed + ", iteration=" + iteration;
            Random random = new Random(seed);

            Map<String, String> values = new LinkedHashMap<>();
            for (String name : PLACEHOLDERS) {
                values.put(name, randomValue(random));
            }

            // Build the template and the independently computed expected output together.
            StringBuilder template = new StringBuilder();
            StringBuilder expected = new StringBuilder();
            int segments = PLACEHOLDERS.size() + random.nextInt(8);
            for (int i = 0; i < segments; i++) {
                String text = randomPlainText(random);
                template.append(text);
                expected.append(text);
                // Guarantee every placeholder appears at least once, then add random repeats.
                String name = i < PLACEHOLDERS.size() ? PLACEHOLDERS.get(i) : PLACEHOLDERS.get(random.nextInt(PLACEHOLDERS.size()));
                template.append('{').append(name).append('}');
                expected.append(values.get(name));
            }
            String tail = randomPlainText(random);
            template.append(tail);
            expected.append(tail);

            String rendered = templateUtility.templateLiteral(template.toString(), values);

            assertEquals(expected.toString(), rendered, "Output differs from literal substitution (" + context + ")");
            for (String name : PLACEHOLDERS) {
                assertTrue(rendered.contains(values.get(name)),
                    "Value for " + name + " not present character-for-character (" + context + ")");
            }
        }
    }

    @Test
    @DisplayName("Property 9: unknown placeholders are left untouched and values are not rescanned")
    void unknownPlaceholdersUntouchedAndNoRescan() {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            long seed = BASE_SEED * 37 + iteration;
            String context = "seed=" + seed + ", iteration=" + iteration;
            Random random = new Random(seed);

            Map<String, String> values = new LinkedHashMap<>();
            values.put("FAILED_LIST", "{DEPOSITED_LIST}" + randomValue(random));
            values.put("DEPOSITED_LIST", "[deposited]" + randomValue(random));

            String prefix = randomPlainText(random);
            String suffix = randomPlainText(random);
            String template = prefix + "{FAILED_LIST}|{NOT_A_PARAMETER}|{DEPOSITED_LIST}" + suffix;
            String expected = prefix + values.get("FAILED_LIST") + "|{NOT_A_PARAMETER}|" + values.get("DEPOSITED_LIST") + suffix;

            assertEquals(expected, templateUtility.templateLiteral(template, values),
                "Unknown placeholder altered or inserted value rescanned (" + context + ")");
        }
    }

    private static String randomValue(Random random) {
        StringBuilder value = new StringBuilder();
        int parts = random.nextInt(8);
        for (int i = 0; i < parts; i++) {
            if (random.nextInt(3) == 0) {
                value.append(randomWord(random));
            } else {
                value.append(NASTY_FRAGMENTS[random.nextInt(NASTY_FRAGMENTS.length)]);
            }
        }
        return value.toString();
    }

    // Plain template text never contains braces so it cannot form extra placeholders.
    private static String randomPlainText(Random random) {
        StringBuilder text = new StringBuilder();
        int length = random.nextInt(12);
        for (int i = 0; i < length; i++) {
            text.append(" abcXYZ019:-#.,\n$\\".charAt(random.nextInt(" abcXYZ019:-#.,\n$\\".length())));
        }
        return text.toString();
    }

    private static String randomWord(Random random) {
        StringBuilder word = new StringBuilder();
        int length = 1 + random.nextInt(8);
        for (int i = 0; i < length; i++) {
            word.append((char) ('a' + random.nextInt(26)));
        }
        return word.toString();
    }

}
