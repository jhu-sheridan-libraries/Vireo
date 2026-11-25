package org.tdl.vireo.model.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class FailureReasonTest {

    @Test
    public void usesTheThrowablesOwnMessage() {
        assertEquals("outer", FailureReason.of(new IllegalStateException("outer", new IOException("root"))));
    }

    @Test
    public void blankMessageFallsBackToTheRootCauseMessage() {
        Throwable t = new IllegalStateException(" ", new RuntimeException(null, new IOException("root")));

        assertEquals("root", FailureReason.of(t));
    }

    @Test
    public void blankMessagesFallBackToTheSimpleClassName() {
        assertEquals("IllegalStateException", FailureReason.of(new IllegalStateException("\t", new IOException(""))));
        assertEquals("IllegalArgumentException", FailureReason.of(new IllegalArgumentException()));
    }

    @Test
    public void causeCycleStillFindsTheLastDistinctCause() {
        Exception first = new Exception(" ");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);

        assertEquals("second", FailureReason.of(first));
    }

    @Test
    public void longMessageIsTruncated() {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < FailureReason.MAX_LENGTH + 1; i++) {
            message.append((char) ('a' + i % 26));
        }

        String reason = FailureReason.of(new RuntimeException(message.toString()));

        assertEquals(message.substring(0, FailureReason.MAX_LENGTH), reason);
    }

    @Test
    public void nullThrowableIsUnknownError() {
        assertEquals("Unknown error", FailureReason.of(null));
    }

}
