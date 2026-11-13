package org.tdl.vireo.model.batch;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Derives a short, human-readable failure reason from a Throwable.
 */
public final class FailureReason {

    public static final int MAX_LENGTH = 1000;

    private FailureReason() {
    }

    /**
     * Returns the throwable's message when non-blank, otherwise the root cause's
     * message when non-blank, otherwise the simple class name. The result is
     * truncated to the first {@link #MAX_LENGTH} characters.
     */
    public static String of(Throwable t) {
        String reason;
        if (t == null) {
            reason = "Unknown error";
        } else if (!isBlank(t.getMessage())) {
            reason = t.getMessage();
        } else {
            Throwable root = rootCause(t);
            if (root != t && !isBlank(root.getMessage())) {
                reason = root.getMessage();
            } else {
                reason = t.getClass().getSimpleName();
            }
        }
        return reason.length() > MAX_LENGTH ? reason.substring(0, MAX_LENGTH) : reason;
    }

    private static Throwable rootCause(Throwable t) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = t;
        visited.add(current);
        while (current.getCause() != null && !visited.contains(current.getCause())) {
            current = current.getCause();
            visited.add(current);
        }
        return current;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
