package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * A precondition failure always carries a message, so it can never look like a run that started.
 */
public class BatchDepositResultTest {

    @Test
    public void preconditionFailedRejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> BatchDepositResult.preconditionFailed(null));
    }

    @Test
    public void preconditionFailedRejectsBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> BatchDepositResult.preconditionFailed(""));
        assertThrows(IllegalArgumentException.class, () -> BatchDepositResult.preconditionFailed("  "));
    }

    @Test
    public void countsEachStatusSeparately() {
        BatchDepositResult result = new BatchDepositResult(Arrays.asList(
            DepositOutcome.deposited(1, "One"),
            DepositOutcome.failed(2, "Two", "r", null),
            DepositOutcome.notRecorded(3, "Three", "http://repo/3", "r", null),
            DepositOutcome.notRecorded(4, null, null, "r", null)));

        assertEquals(1, result.deposited());
        assertEquals(1, result.failed());
        assertEquals(2, result.notRecorded());
        assertEquals(4, result.total());
    }

    @Test
    public void preconditionFailedKeepsMessageAndHasNoOutcomes() {
        BatchDepositResult result = BatchDepositResult.preconditionFailed("No deposit location");

        assertTrue(result.isPreconditionFailed());
        assertEquals("No deposit location", result.getPreconditionError());
        assertEquals(0, result.total());
    }

}
