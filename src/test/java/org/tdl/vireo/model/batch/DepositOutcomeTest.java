package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class DepositOutcomeTest {

    @Test
    public void blankTitlesAreAbsent() {
        assertNull(DepositOutcome.failed(1, null, "r", null).getTitle());
        assertNull(DepositOutcome.failed(1, "", "r", null).getTitle());
        assertNull(DepositOutcome.deposited(1, " \n").getTitle());
        assertEquals("Title", DepositOutcome.deposited(1, "Title").getTitle());
    }

}
