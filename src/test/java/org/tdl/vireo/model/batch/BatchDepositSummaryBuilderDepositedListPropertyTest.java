package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

// Feature: batch-deposit-summary-email, Property 7: Deposited list matches deposited outcomes
// Validates: Requirements 3.8, 3.9
public class BatchDepositSummaryBuilderDepositedListPropertyTest {

    private static final long BASE_SEED = 70707070L;
    private static final int ITERATIONS = 200;
    private static final int[] BOUNDARY_SIZES = { 0, 1, 499, 500, 501, 1000 };
    private static final int MAX_SIZE = 1000;
    private static final int CAP = 500;

    private static final String[] TITLES = {
        null, "", "   ", "\t \n", "Plain title", "Line one\nLine two", "Carriage\r\nreturn\rtitle",
        "$1 $& ${x} $$", "back\\slash \\1 \\\\ \\$", "{DEPOSIT_LOCATION} {DEPOSITED_COUNT}", "Ünïcödé – title"
    };

    private static final String[] REASONS = { "boom", "Connection refused", "multi\nline\nreason" };

    private final BatchDepositSummaryBuilder builder = new BatchDepositSummaryBuilder();

    @Test
    public void depositedListMatchesDepositedOutcomes() {
        for (int i = 0; i < ITERATIONS; i++) {
            long seed = BASE_SEED * 31 + i;
            String ctx = " [seed=" + seed + ", iteration=" + i + "]";
            Random random = new Random(seed);

            int size = i < BOUNDARY_SIZES.length ? BOUNDARY_SIZES[i] : random.nextInt(MAX_SIZE + 1);
            // Boundary sizes: all deposited, so D hits the boundary itself.
            // Next block: none deposited. After that: all, none, or a random mix.
            double depositedRatio;
            if (i < BOUNDARY_SIZES.length) {
                depositedRatio = 1.0;
            } else if (i < 2 * BOUNDARY_SIZES.length) {
                depositedRatio = 0.0;
            } else {
                int mode = random.nextInt(4);
                depositedRatio = mode == 0 ? 1.0 : mode == 1 ? 0.0 : random.nextDouble();
            }

            List<DepositOutcome> outcomes = new ArrayList<>(size);
            List<String> expected = new ArrayList<>();
            int depositedCount = 0;
            for (int n = 0; n < size; n++) {
                long id = 1000L + random.nextInt(1_000_000) * 1000L + n; // unique, not in order
                String title = TITLES[random.nextInt(TITLES.length)];
                if (random.nextDouble() < depositedRatio) {
                    outcomes.add(DepositOutcome.deposited(id, title));
                    depositedCount++;
                    if (expected.size() < CAP) {
                        expected.add("- #" + id + " " + expectedTitle(title));
                    }
                } else {
                    String reason = REASONS[random.nextInt(REASONS.length)];
                    outcomes.add(DepositOutcome.failed(id, title, reason, new RuntimeException("x")));
                }
            }

            SummaryContent content = builder.build("Location", outcomes, ZonedDateTime.now(ZoneId.of("UTC")));
            List<String> lines = content.getDepositedLines();
            String text = content.getParameters().get("DEPOSITED_LIST");

            assertEquals(expected, lines, "deposited entries must be the first min(D,500) deposits in order" + ctx);
            assertEquals(Math.min(depositedCount, CAP), lines.size(), "entry count" + ctx);
            assertEquals(String.valueOf(depositedCount), content.getParameters().get("DEPOSITED_COUNT"), "deposited count" + ctx);

            List<String> textLines = Arrays.asList(text.split("\n", -1));
            if (depositedCount == 0) {
                assertEquals("No submissions were deposited.", text, "no-deposits text" + ctx);
                assertTrue(lines.isEmpty(), "no entries when none deposited" + ctx);
            } else if (depositedCount <= CAP) {
                assertEquals(expected, textLines, "list text is exactly the entries" + ctx);
            } else {
                List<String> withNote = new ArrayList<>(expected);
                withNote.add("... and " + (depositedCount - CAP) + " more not listed");
                assertEquals(withNote, textLines, "list text has entries plus truncation note" + ctx);
            }
        }
    }

    private static String expectedTitle(String title) {
        return title == null || title.trim().isEmpty() ? "(no title)" : collapse(title);
    }

    private static String collapse(String value) {
        StringBuilder sb = new StringBuilder();
        boolean inBreak = false;
        for (char c : value.toCharArray()) {
            if (c == '\n' || c == '\r') {
                if (!inBreak) {
                    sb.append(' ');
                }
                inBreak = true;
            } else {
                sb.append(c);
                inBreak = false;
            }
        }
        return sb.toString();
    }

}
