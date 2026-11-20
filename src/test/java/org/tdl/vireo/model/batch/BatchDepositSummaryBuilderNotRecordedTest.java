package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Deposited-but-not-recorded outcomes in the summary, with the current template and with a
 * template that has no NOT_RECORDED_LIST placeholder.
 */
public class BatchDepositSummaryBuilderNotRecordedTest {

    private static final ZonedDateTime FINISHED_AT = ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneId.of("UTC"));

    private final List<DepositOutcome> outcomes = Arrays.asList(
        DepositOutcome.deposited(1, "One"),
        DepositOutcome.failed(2, "Two", "packager broke", new RuntimeException("packager broke")),
        DepositOutcome.notRecorded(3, "Three", "http://repo.example.org/handle/3", "action log failed", new RuntimeException("action log failed")),
        DepositOutcome.notRecorded(4, null, null, "line\nbreak", new RuntimeException("x")));

    @Test
    public void notRecordedIsCountedAndListedSeparately() {
        Map<String, String> p = new BatchDepositSummaryBuilder().build("Repo", outcomes, FINISHED_AT).getParameters();

        assertEquals("4", p.get("TOTAL_COUNT"));
        assertEquals("1", p.get("DEPOSITED_COUNT"));
        assertEquals("1", p.get("FAILED_COUNT"));
        assertEquals("2", p.get("NOT_RECORDED_COUNT"));
        assertEquals("- #3 Three (http://repo.example.org/handle/3): action log failed\n"
            + "- #4 " + BatchDepositSummaryBuilder.NO_TITLE + " (" + BatchDepositSummaryBuilder.NO_DEPOSIT_URL + "): line break",
            p.get("NOT_RECORDED_LIST"));
        assertEquals("- #2 Two: packager broke", p.get("FAILED_LIST"));
        assertEquals("- #1 One", p.get("DEPOSITED_LIST"));
    }

    @Test
    public void noNotRecordedShowsNoneText() {
        Map<String, String> p = new BatchDepositSummaryBuilder()
            .build("Repo", Arrays.asList(DepositOutcome.deposited(1, "One")), FINISHED_AT).getParameters();

        assertEquals("0", p.get("NOT_RECORDED_COUNT"));
        assertEquals(BatchDepositSummaryBuilder.NO_NOT_RECORDED, p.get("NOT_RECORDED_LIST"));
    }

    @Test
    public void legacyTemplateListsNotRecordedUnderFailedAndCountsThem() {
        SummaryContent content = new BatchDepositSummaryBuilder().build("Repo", outcomes, FINISHED_AT, true);
        Map<String, String> p = content.getParameters();

        assertEquals("4", p.get("TOTAL_COUNT"));
        assertEquals("1", p.get("DEPOSITED_COUNT"));
        assertEquals("3", p.get("FAILED_COUNT"), "failed plus not recorded with a legacy template");
        String failedList = p.get("FAILED_LIST");
        assertEquals(3, failedList.split("\n").length, failedList);
        assertTrue(failedList.contains("- #2 Two: packager broke"), failedList);
        assertTrue(failedList.contains("- #3 Three (http://repo.example.org/handle/3): " + BatchDepositSummaryBuilder.NOT_RECORDED_MARKER + ": action log failed"), failedList);
        assertTrue(failedList.contains("- #4 " + BatchDepositSummaryBuilder.NO_TITLE + " (" + BatchDepositSummaryBuilder.NO_DEPOSIT_URL + "): " + BatchDepositSummaryBuilder.NOT_RECORDED_MARKER), failedList);
        assertEquals(3, content.getFailedLines().size());
    }

    @Test
    public void legacyTemplateWithoutNotRecordedMatchesCurrentOutput() {
        List<DepositOutcome> plain = Arrays.asList(
            DepositOutcome.deposited(1, "One"),
            DepositOutcome.failed(2, "Two", "boom", new RuntimeException("boom")));

        assertEquals(
            new BatchDepositSummaryBuilder().build("Repo", plain, FINISHED_AT, false).getParameters(),
            new BatchDepositSummaryBuilder().build("Repo", plain, FINISHED_AT, true).getParameters());
    }

    @Test
    public void notRecordedListIsCapped() {
        int total = BatchDepositSummaryBuilder.MAX_ENTRIES + 3;
        List<DepositOutcome> many = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            many.add(DepositOutcome.notRecorded(i, "T" + i, "http://repo/" + i, "r", new RuntimeException("r")));
        }

        SummaryContent content = new BatchDepositSummaryBuilder().build("Repo", many, FINISHED_AT);

        assertEquals(BatchDepositSummaryBuilder.MAX_ENTRIES, content.getNotRecordedLines().size());
        assertTrue(content.getParameters().get("NOT_RECORDED_LIST").endsWith("... and 3 more not listed"));
        assertFalse(content.getParameters().get("FAILED_LIST").contains("http://repo/"));
    }

    @Test
    public void resultCountsEachStatusSeparately() {
        BatchDepositResult result = new BatchDepositResult(outcomes);

        assertEquals(1, result.deposited());
        assertEquals(1, result.failed());
        assertEquals(2, result.notRecorded());
        assertEquals(4, result.total());
    }

}
