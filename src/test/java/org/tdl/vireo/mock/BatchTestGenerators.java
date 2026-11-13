package org.tdl.vireo.mock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

import org.tdl.vireo.model.batch.DepositOutcome;

/**
 * Seeded, dependency-free generators for the batch deposit summary tests.
 *
 * Every generator is built on a {@link Random} created from an explicit seed, so any
 * iteration can be replayed from the seed reported in a failure message. Generated
 * inputs are not shrunk.
 */
public final class BatchTestGenerators {

    /** Fixed outcome-list sizes that every list-driven property must cover. */
    public static final int[] BOUNDARY_SIZES = { 0, 1, 499, 500, 501, 1000 };

    public static final int MAX_SIZE = 1000;

    public static final int MIN_ITERATIONS = 100;

    private static final String[] ZONES = { "UTC", "America/New_York", "America/Los_Angeles", "Europe/London", "Asia/Kolkata", "Asia/Tokyo", "Australia/Adelaide", "Pacific/Auckland",
            "Pacific/Chatham", "America/St_Johns" };

    private static final String[] TITLE_TOKENS = { "Thesis", "on", "the", "Theory", "of", "Everything", "$1", "$", "\\", "\\$", "$&", "${x}", "{FAILED_LIST}", "{TOTAL_COUNT}", "\u00e9",
            "-", "#" };

    private final Random random;

    private final long seed;

    public BatchTestGenerators(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long getSeed() {
        return seed;
    }

    public Random random() {
        return random;
    }

    // ---------------------------------------------------------------- iteration

    /**
     * Derives the seed of one iteration from the base seed and the iteration number.
     */
    public static long iterationSeed(long baseSeed, int iteration) {
        long z = baseSeed + (iteration + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Runs the body for each iteration with a generator seeded from the base seed and the
     * iteration number. Any failure is rethrown as an AssertionError whose message carries
     * the base seed, the iteration, and the derived seed.
     *
     * @param baseSeed
     *            fixed base seed of the calling test
     * @param iterations
     *            number of iterations, at least {@value #MIN_ITERATIONS}
     * @param body
     *            receives the generator and the iteration number
     */
    public static void runIterations(long baseSeed, int iterations, BiConsumer<BatchTestGenerators, Integer> body) {
        if (iterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException("At least " + MIN_ITERATIONS + " iterations are required, got " + iterations);
        }
        for (int i = 0; i < iterations; i++) {
            long iterationSeed = iterationSeed(baseSeed, i);
            try {
                body.accept(new BatchTestGenerators(iterationSeed), i);
            } catch (Throwable t) {
                AssertionError error = new AssertionError("seed=" + baseSeed + ", iteration=" + i + " (iterationSeed=" + iterationSeed + "): " + t, t);
                throw error;
            }
        }
    }

    // -------------------------------------------------------------------- sizes

    /**
     * Size for a given iteration: the fixed boundary sizes for the first iterations, then
     * random sizes from 0 to {@link #MAX_SIZE}.
     */
    public int sizeForIteration(int iteration) {
        if (iteration >= 0 && iteration < BOUNDARY_SIZES.length) {
            return BOUNDARY_SIZES[iteration];
        }
        return random.nextInt(MAX_SIZE + 1);
    }

    // ------------------------------------------------------------------- titles

    /**
     * A title that may be null, empty, blank, multi-line, or full of $ and \ characters.
     */
    public String title() {
        switch (random.nextInt(7)) {
        case 0:
            return null;
        case 1:
            return "";
        case 2:
            return blank();
        case 3:
            return multiLine();
        case 4:
            return specialChars();
        default:
            return words(1 + random.nextInt(8), " ");
        }
    }

    public String blank() {
        String[] chars = { " ", "\t", "\n", "\r\n" };
        StringBuilder sb = new StringBuilder();
        int n = 1 + random.nextInt(5);
        for (int i = 0; i < n; i++) {
            sb.append(chars[random.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    public String multiLine() {
        String[] separators = { "\n", "\r\n", "\r" };
        StringBuilder sb = new StringBuilder(words(1 + random.nextInt(3), " "));
        int lines = 1 + random.nextInt(3);
        for (int i = 0; i < lines; i++) {
            sb.append(separators[random.nextInt(separators.length)]).append(words(1 + random.nextInt(3), " "));
        }
        return sb.toString();
    }

    public String specialChars() {
        String[] pieces = { "$", "\\", "$1", "\\$", "$$", "\\\\", "${name}", "{DEPOSITED_LIST}", "{FAILED_LIST}", "{", "}", "x" };
        StringBuilder sb = new StringBuilder();
        int n = 1 + random.nextInt(10);
        for (int i = 0; i < n; i++) {
            sb.append(pieces[random.nextInt(pieces.length)]);
        }
        return sb.toString();
    }

    private String words(int count, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(TITLE_TOKENS[random.nextInt(TITLE_TOKENS.length)]);
        }
        return sb.toString();
    }

    /**
     * A non-blank text for messages and failure reasons.
     */
    public String nonBlankText() {
        String text = random.nextInt(3) == 0 ? specialChars() : words(1 + random.nextInt(6), " ");
        return text.trim().isEmpty() ? "reason" : text;
    }

    /**
     * A message that is null, empty, blank, or non-blank.
     */
    public String message() {
        switch (random.nextInt(4)) {
        case 0:
            return null;
        case 1:
            return "";
        case 2:
            return blank();
        default:
            return random.nextInt(8) == 0 ? longText(1001 + random.nextInt(2000)) : nonBlankText();
        }
    }

    public String longText(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    // ----------------------------------------------------------------- outcomes

    public DepositOutcome outcome(long id) {
        String title = title();
        if (random.nextBoolean()) {
            return DepositOutcome.deposited(id, title);
        }
        Throwable cause = throwableChain();
        String reason = nonBlankText();
        return DepositOutcome.failed(id, title, reason, cause);
    }

    /**
     * An outcome list of exactly the given size with distinct ids, in random order.
     */
    public List<DepositOutcome> outcomes(int size) {
        List<DepositOutcome> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(outcome(1L + i));
        }
        Collections.shuffle(list, random);
        return list;
    }

    /**
     * An outcome list of a random size from 0 to {@link #MAX_SIZE}.
     */
    public List<DepositOutcome> outcomes() {
        return outcomes(random.nextInt(MAX_SIZE + 1));
    }

    /**
     * An outcome list sized by iteration number: boundary sizes first, then random sizes.
     */
    public List<DepositOutcome> outcomesForIteration(int iteration) {
        return outcomes(sizeForIteration(iteration));
    }

    // -------------------------------------------------------------- throwables

    /**
     * A throwable with a cause chain of 0 to 4 causes. Messages may be null, empty, blank,
     * or non-blank. One chain in five contains a cause cycle.
     */
    public Throwable throwableChain() {
        return chain(1 + random.nextInt(5), random.nextInt(5) == 0);
    }

    /**
     * A throwable chain of 2 to 5 elements whose last cause points back to an earlier
     * element, so walking getCause() never terminates without a guard.
     */
    public Throwable cyclicThrowable() {
        return chain(2 + random.nextInt(4), true);
    }

    private Throwable chain(int depth, boolean cyclic) {
        List<LinkedException> chain = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            chain.add(new LinkedException(message()));
        }
        for (int i = 0; i < depth - 1; i++) {
            chain.get(i).next = chain.get(i + 1);
        }
        if (cyclic && depth > 1) {
            chain.get(depth - 1).next = chain.get(random.nextInt(depth - 1));
        }
        return chain.get(0);
    }

    /** Exception with a freely settable cause, so a cause cycle can be built. */
    private static final class LinkedException extends Exception {
        private static final long serialVersionUID = 1L;

        private transient Throwable next;

        LinkedException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return next;
        }
    }

    // ------------------------------------------------------------------- times

    /**
     * A zoned time at a random instant (1970 to roughly 2100) in a random zone.
     */
    public ZonedDateTime zonedDateTime() {
        long epochSecond = (long) (random.nextDouble() * 4_102_444_800L);
        ZoneId zone = ZoneId.of(ZONES[random.nextInt(ZONES.length)]);
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), zone);
    }

    public static List<String> zoneIds() {
        return Collections.unmodifiableList(Arrays.asList(ZONES));
    }

}
