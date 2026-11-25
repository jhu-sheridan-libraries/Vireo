package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

public class BatchDepositSummaryBuilderFinishedAtTest {

    private static String finishedAt(ZonedDateTime time) {
        return new BatchDepositSummaryBuilder().build("Repo", new ArrayList<>(), time).getParameters().get("FINISHED_AT");
    }

    @Test
    public void showsDateTimeAndZone() {
        assertEquals("2024-05-06 07:08:09 UTC", finishedAt(ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneId.of("UTC"))));
    }

    @Test
    public void showsLocalTimeAndTheZoneNameInEffect() {
        ZoneId newYork = ZoneId.of("America/New_York");
        String winter = finishedAt(ZonedDateTime.of(2024, 1, 15, 23, 30, 0, 0, newYork));
        String summer = finishedAt(ZonedDateTime.of(2024, 7, 15, 23, 30, 0, 0, newYork));

        assertTrue(winter.startsWith("2024-01-15 23:30:00 "), winter);
        assertTrue(summer.startsWith("2024-07-15 23:30:00 "), summer);
        // Standard and daylight time have different zone names (EST and EDT in English).
        assertNotEquals(winter.substring(20), summer.substring(20));
    }

}
