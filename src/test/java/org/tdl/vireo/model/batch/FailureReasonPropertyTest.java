package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Feature: batch-deposit-summary-email, Property 3: Failure reason derivation
public class FailureReasonPropertyTest {

    private static final long BASE_SEED = 20240603L;
    private static final int ITERATIONS = 300;

    private static final String[] BLANKS = { null, "", " ", "   ", "\t", "\n", " \t\r\n " };

    @Test
    public void failureReasonFollowsDerivationRule() {
        for (int i = 0; i < ITERATIONS; i++) {
            long seed = BASE_SEED * 31 + i;
            String ctx = " [seed=" + seed + ", iteration=" + i + "]";
            Random random = new Random(seed);

            Throwable t = randomChain(random);
            String expected = expectedUntruncated(t);
            String actual = FailureReason.of(t);

            assertTrue(actual.length() <= 1000, "length must be <= 1000" + ctx);
            assertTrue(expected.startsWith(actual), "result must be a prefix of untruncated value" + ctx);
            assertEquals(Math.min(expected.length(), 1000), actual.length(), "truncation must keep first 1000 chars" + ctx);
        }
    }

    // Independent oracle for the untruncated value.
    private static String expectedUntruncated(Throwable t) {
        if (!isBlank(t.getMessage())) {
            return t.getMessage();
        }
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = t;
        visited.add(current);
        while (current.getCause() != null && !visited.contains(current.getCause())) {
            current = current.getCause();
            visited.add(current);
        }
        if (current != t && !isBlank(current.getMessage())) {
            return current.getMessage();
        }
        return t.getClass().getSimpleName();
    }

    private static Throwable randomChain(Random random) {
        int length = 1 + random.nextInt(4);
        Throwable[] chain = new Throwable[length];
        for (int i = 0; i < length; i++) {
            chain[i] = randomThrowable(random, randomMessage(random));
        }
        for (int i = 0; i < length - 1; i++) {
            chain[i].initCause(chain[i + 1]);
        }
        // Occasionally close the chain into a cycle.
        if (length > 1 && random.nextInt(4) == 0) {
            chain[length - 1].initCause(chain[random.nextInt(length - 1)]);
        }
        return chain[0];
    }

    private static Throwable randomThrowable(Random random, String message) {
        switch (random.nextInt(3)) {
        case 0:
            return new IllegalStateException(message);
        case 1:
            return new IllegalArgumentException(message);
        default:
            return new java.io.IOException(message);
        }
    }

    private static String randomMessage(Random random) {
        switch (random.nextInt(4)) {
        case 0:
            return BLANKS[random.nextInt(BLANKS.length)];
        case 1:
            return randomText(random, 1 + random.nextInt(50));
        case 2:
            return randomText(random, 990 + random.nextInt(21)); // around the boundary
        default:
            return randomText(random, 1001 + random.nextInt(3000));
        }
    }

    private static String randomText(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append((char) ('a' + random.nextInt(26))); // ensure non-blank
        for (int i = 1; i < length; i++) {
            sb.append((char) (' ' + random.nextInt(95)));
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
