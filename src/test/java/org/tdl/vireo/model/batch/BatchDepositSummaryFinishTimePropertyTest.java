package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

// Feature: batch-deposit-summary-email, Property 8: Finish time carries date, time, and zone
// Validates: Requirements 3.4
public class BatchDepositSummaryFinishTimePropertyTest {

    private static final long BASE_SEED = 80808080L;
    private static final int ITERATIONS = 200;
    private static final String[] ZONES = {
        "UTC", "America/New_York", "America/Los_Angeles", "Europe/London", "Europe/Paris",
        "Asia/Kolkata", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland", "Pacific/Honolulu"
    };
    // 1970-01-01 .. roughly 2100-01-01
    private static final long MAX_EPOCH_SECOND = 4_102_444_800L;

    private final BatchDepositSummaryBuilder builder = new BatchDepositSummaryBuilder();

    @Test
    public void finishTimeCarriesDateTimeAndZone() {
        for (int i = 0; i < ITERATIONS; i++) {
            long seed = BASE_SEED * 31 + i;
            String ctx = " [seed=" + seed + ", iteration=" + i + "]";
            Random random = new Random(seed);

            ZoneId zone = ZoneId.of(ZONES[random.nextInt(ZONES.length)]);
            long epochSecond = (long) (random.nextDouble() * MAX_EPOCH_SECOND);
            Instant instant = Instant.ofEpochSecond(epochSecond, random.nextInt(1_000_000_000));
            ZonedDateTime finishedAt = ZonedDateTime.ofInstant(instant, zone);

            List<DepositOutcome> outcomes = new ArrayList<>();
            if (random.nextBoolean()) {
                outcomes.add(DepositOutcome.deposited(1L, "Title"));
            }

            String finished = builder.build("Location", outcomes, finishedAt).getParameters().get("FINISHED_AT");

            // Expected fields computed independently from the instant and zone.
            ZonedDateTime local = instant.atZone(zone);
            String expectedDate = String.format("%04d-%02d-%02d", local.getYear(), local.getMonthValue(), local.getDayOfMonth());
            String expectedTime = String.format("%02d:%02d:%02d", local.getHour(), local.getMinute(), local.getSecond());
            String zoneName = DateTimeFormatter.ofPattern("z").format(local);

            assertTrue(finished.contains(expectedDate), "FINISHED_AT '" + finished + "' must contain date " + expectedDate + ctx);
            assertTrue(finished.contains(expectedTime), "FINISHED_AT '" + finished + "' must contain time " + expectedTime + ctx);
            assertTrue(finished.endsWith(zoneName), "FINISHED_AT '" + finished + "' must end with zone " + zoneName + ctx);
            assertEquals(expectedDate + " " + expectedTime + " " + zoneName, finished, "FINISHED_AT layout" + ctx);
        }
    }

}
