package org.tdl.vireo.model.batch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values for the batch deposit summary email placeholders. The line lists hold only the
 * entries that were listed; the "... and N more" and "none" texts appear only in the values.
 */
public final class SummaryContent {

    private final Map<String, String> parameters;

    private final List<String> depositedLines;

    private final List<String> failedLines;

    private final List<String> notRecordedLines;

    SummaryContent(Map<String, String> parameters, List<String> depositedLines, List<String> failedLines, List<String> notRecordedLines) {
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.depositedLines = Collections.unmodifiableList(depositedLines);
        this.failedLines = Collections.unmodifiableList(failedLines);
        this.notRecordedLines = Collections.unmodifiableList(notRecordedLines);
    }

    /**
     * @return placeholder name (without braces) to literal value.
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    public List<String> getDepositedLines() {
        return depositedLines;
    }

    public List<String> getFailedLines() {
        return failedLines;
    }

    public List<String> getNotRecordedLines() {
        return notRecordedLines;
    }

}
