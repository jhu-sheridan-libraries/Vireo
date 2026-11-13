package org.tdl.vireo.model.batch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values for the batch deposit summary email placeholders.
 *
 * {@link #getDepositedLines()} and {@link #getFailedLines()} contain only the
 * entry lines that were listed (at most {@link BatchDepositSummaryBuilder#MAX_ENTRIES}
 * each). The "... and N more not listed" line and the "none" statements appear
 * only in the DEPOSITED_LIST and FAILED_LIST placeholder text.
 */
public final class SummaryContent {

    private final Map<String, String> parameters;

    private final List<String> depositedLines;

    private final List<String> failedLines;

    SummaryContent(Map<String, String> parameters, List<String> depositedLines, List<String> failedLines) {
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.depositedLines = Collections.unmodifiableList(depositedLines);
        this.failedLines = Collections.unmodifiableList(failedLines);
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

}
