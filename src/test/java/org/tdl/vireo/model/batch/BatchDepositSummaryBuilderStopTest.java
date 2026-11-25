package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The stopped-early note in FAILED_LIST.
 */
public class BatchDepositSummaryBuilderStopTest {

    private static final ZonedDateTime FINISHED_AT = ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneId.of("UTC"));

    private final List<DepositOutcome> outcomes = Arrays.asList(
        DepositOutcome.deposited(1, "One"),
        DepositOutcome.failed(2, "Two", "Java heap space", new OutOfMemoryError("Java heap space")),
        DepositOutcome.failed(3, "Three", BatchDepositSummaryBuilder.notAttemptedReason(2), null),
        DepositOutcome.failed(4, "Four", BatchDepositSummaryBuilder.notAttemptedReason(2), null));

    @Test
    public void noteComesFirstAndIsNotCounted() {
        SummaryContent content = new BatchDepositSummaryBuilder().build("Repo", outcomes, FINISHED_AT, false, new BatchStop(2, 2));
        Map<String, String> p = content.getParameters();

        String[] lines = p.get("FAILED_LIST").split("\n");
        assertEquals("The batch stopped early after an internal error on submission #2. 2 submissions were not attempted; they are listed below as failed and can be retried.", lines[0]);
        assertEquals("- #2 Two: Java heap space", lines[1]);
        assertEquals("- #3 Three: Not attempted: the batch stopped after an internal error on submission #2", lines[2]);
        assertEquals(4, lines.length);
        assertEquals("3", p.get("FAILED_COUNT"));
        assertEquals("4", p.get("TOTAL_COUNT"));
        assertEquals(3, content.getFailedLines().size());
    }

    @Test
    public void noteWordingForOneAndNone() {
        assertTrue(BatchDepositSummaryBuilder.stoppedNote(new BatchStop(5, 1))
            .endsWith("1 submission was not attempted; it is listed below as failed and can be retried."));
        assertTrue(BatchDepositSummaryBuilder.stoppedNote(new BatchStop(5, 0))
            .endsWith("Every other submission had already been attempted."));
    }

    @Test
    public void noteAppearsEvenWhenNothingFailed() {
        // An Error in workflow emails on the last submission: it stays Deposited.
        Map<String, String> p = new BatchDepositSummaryBuilder()
            .build("Repo", Arrays.asList(DepositOutcome.deposited(1, "One")), FINISHED_AT, false, new BatchStop(1, 0)).getParameters();

        assertEquals(BatchDepositSummaryBuilder.stoppedNote(new BatchStop(1, 0)) + "\n" + BatchDepositSummaryBuilder.NO_FAILURES, p.get("FAILED_LIST"));
        assertEquals("0", p.get("FAILED_COUNT"));
    }

    @Test
    public void noteIsNotHiddenByTheEntryLimit() {
        int total = BatchDepositSummaryBuilder.MAX_ENTRIES + 10;
        List<DepositOutcome> many = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            many.add(DepositOutcome.failed(i, "T" + i, "r", null));
        }

        SummaryContent content = new BatchDepositSummaryBuilder().build("Repo", many, FINISHED_AT, false, new BatchStop(0, total - 1));
        String failedList = content.getParameters().get("FAILED_LIST");

        assertTrue(failedList.startsWith("The batch stopped early after an internal error on submission #0."), failedList.substring(0, 80));
        assertTrue(failedList.endsWith("... and 10 more not listed"));
        assertEquals(BatchDepositSummaryBuilder.MAX_ENTRIES, content.getFailedLines().size());
    }


}
