package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The deposited and failed lists and the counts in the summary placeholders.
 */
public class BatchDepositSummaryBuilderListsTest {

    private static final ZonedDateTime FINISHED_AT = ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneId.of("UTC"));

    private final BatchDepositSummaryBuilder builder = new BatchDepositSummaryBuilder();

    @Test
    public void listsKeepInputOrderAndFlattenTitlesAndReasons() {
        List<DepositOutcome> outcomes = Arrays.asList(
            DepositOutcome.failed(30, "Line one\nLine two", "multi\nline\r\nreason", null),
            DepositOutcome.deposited(10, null),
            DepositOutcome.failed(5, "  ", "boom", null),
            DepositOutcome.deposited(20, "Carriage\r\nreturn\rtitle"),
            DepositOutcome.deposited(1, "$1 \\ {FAILED_LIST}"));

        Map<String, String> p = builder.build("Repo", outcomes, FINISHED_AT).getParameters();

        assertEquals("5", p.get("TOTAL_COUNT"));
        assertEquals("3", p.get("DEPOSITED_COUNT"));
        assertEquals("2", p.get("FAILED_COUNT"));
        assertEquals("- #10 (no title)\n- #20 Carriage return title\n- #1 $1 \\ {FAILED_LIST}", p.get("DEPOSITED_LIST"));
        assertEquals("- #30 Line one Line two: multi line reason\n- #5 (no title): boom", p.get("FAILED_LIST"));
    }

    @Test
    public void emptyRunShowsZeroCountsAndNoneTexts() {
        Map<String, String> p = builder.build("Repo", new ArrayList<>(), FINISHED_AT).getParameters();

        assertEquals("0", p.get("TOTAL_COUNT"));
        assertEquals("0", p.get("DEPOSITED_COUNT"));
        assertEquals("0", p.get("FAILED_COUNT"));
        assertEquals(BatchDepositSummaryBuilder.NO_DEPOSITS, p.get("DEPOSITED_LIST"));
        assertEquals(BatchDepositSummaryBuilder.NO_FAILURES, p.get("FAILED_LIST"));
    }

    @Test
    public void listsAreCappedWithANoteForTheRest() {
        int max = BatchDepositSummaryBuilder.MAX_ENTRIES;
        List<DepositOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i <= max; i++) {
            outcomes.add(DepositOutcome.deposited(i, "D" + i));
            outcomes.add(DepositOutcome.failed(10_000 + i, "F" + i, "r", null));
        }

        SummaryContent content = builder.build("Repo", outcomes, FINISHED_AT);
        Map<String, String> p = content.getParameters();

        assertEquals(max, content.getDepositedLines().size());
        assertEquals(max, content.getFailedLines().size());
        assertEquals(String.valueOf(max + 1), p.get("DEPOSITED_COUNT"));
        assertEquals("- #" + (max - 1) + " D" + (max - 1) + "\n... and 1 more not listed", tail(p.get("DEPOSITED_LIST")));
        assertEquals("- #" + (10_000 + max - 1) + " F" + (max - 1) + ": r\n... and 1 more not listed", tail(p.get("FAILED_LIST")));
    }

    @Test
    public void listAtTheCapHasNoNote() {
        List<DepositOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < BatchDepositSummaryBuilder.MAX_ENTRIES; i++) {
            outcomes.add(DepositOutcome.deposited(i, "D" + i));
        }

        String list = builder.build("Repo", outcomes, FINISHED_AT).getParameters().get("DEPOSITED_LIST");

        assertFalse(list.contains("more not listed"));
    }

    /** The last two lines of a list. */
    private static String tail(String list) {
        String[] lines = list.split("\n");
        return lines[lines.length - 2] + "\n" + lines[lines.length - 1];
    }

}
