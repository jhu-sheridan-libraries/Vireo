package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

// Feature: batch-deposit-summary-email, Property 5: Summary counts are consistent
// Validates: Requirements 3.2, 3.3, 8.4
public class BatchDepositSummaryCountsPropertyTest {

    private static final long BASE_SEED = 20240605L;
    private static final int ITERATIONS = 200;
    private static final int[] BOUNDARY_SIZES = { 0, 1, 499, 500, 501, 1000 };
    private static final int MAX_SIZE = 1000;

    private final BatchDepositSummaryBuilder builder = new BatchDepositSummaryBuilder();

    @Test
    public void summaryCountsMatchOutcomes() {
        for (int i = 0; i < ITERATIONS; i++) {
            long seed = BASE_SEED * 31 + i;
            String ctx = " [seed=" + seed + ", iteration=" + i + "]";
            Random random = new Random(seed);

            int size = i < BOUNDARY_SIZES.length ? BOUNDARY_SIZES[i] : random.nextInt(MAX_SIZE + 1);
            List<DepositOutcome> outcomes = randomOutcomes(random, size);

            int expectedDeposited = 0;
            for (DepositOutcome outcome : outcomes) {
                if (outcome.isDeposited()) {
                    expectedDeposited++;
                }
            }
            int expectedFailed = size - expectedDeposited;

            Map<String, String> p = builder.build("Location", outcomes, ZonedDateTime.now(ZoneId.of("UTC"))).getParameters();
            int total = Integer.parseInt(p.get("TOTAL_COUNT"));
            int deposited = Integer.parseInt(p.get("DEPOSITED_COUNT"));
            int failed = Integer.parseInt(p.get("FAILED_COUNT"));

            assertEquals(size, total, "total must equal list size" + ctx);
            assertEquals(expectedDeposited, deposited, "deposited count" + ctx);
            assertEquals(expectedFailed, failed, "failed count" + ctx);
            assertEquals(total, deposited + failed, "deposited + failed must equal total" + ctx);
        }
    }

    @Test
    public void emptyOutcomeListShowsZeroCounts() {
        Map<String, String> p = builder.build("Location", new ArrayList<>(), ZonedDateTime.now(ZoneId.of("UTC"))).getParameters();
        assertEquals("0", p.get("TOTAL_COUNT"));
        assertEquals("0", p.get("DEPOSITED_COUNT"));
        assertEquals("0", p.get("FAILED_COUNT"));
    }

    private static List<DepositOutcome> randomOutcomes(Random random, int size) {
        // Vary the deposited/failed mix per list, including all-one-kind lists.
        double depositedRatio = random.nextInt(4) == 0 ? (random.nextBoolean() ? 0.0 : 1.0) : random.nextDouble();
        List<DepositOutcome> outcomes = new ArrayList<>(size);
        for (int n = 0; n < size; n++) {
            long id = n + 1L;
            String title = random.nextInt(5) == 0 ? null : "Title " + random.nextInt(1000);
            if (random.nextDouble() < depositedRatio) {
                outcomes.add(DepositOutcome.deposited(id, title));
            } else {
                outcomes.add(DepositOutcome.failed(id, title, "reason " + random.nextInt(100), new RuntimeException("x")));
            }
        }
        return outcomes;
    }

}
