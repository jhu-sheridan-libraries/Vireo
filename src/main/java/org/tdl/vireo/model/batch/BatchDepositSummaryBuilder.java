package org.tdl.vireo.model.batch;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the placeholder values for the batch deposit summary email.
 *
 * Pure: performs no I/O.
 */
public final class BatchDepositSummaryBuilder {

    public static final int MAX_ENTRIES = 500;

    public static final String NO_TITLE = "(no title)";

    public static final String NO_FAILURES = "No deposits failed.";

    public static final String NO_DEPOSITS = "No submissions were deposited.";

    private static final DateTimeFormatter FINISHED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public SummaryContent build(String depositLocationName, List<DepositOutcome> outcomes, ZonedDateTime finishedAt) {
        List<DepositOutcome> all = outcomes == null ? new ArrayList<>() : outcomes;

        List<String> depositedLines = new ArrayList<>();
        List<String> failedLines = new ArrayList<>();
        int deposited = 0;
        int failed = 0;

        for (DepositOutcome outcome : all) {
            if (outcome.isDeposited()) {
                deposited++;
                if (depositedLines.size() < MAX_ENTRIES) {
                    depositedLines.add("- #" + outcome.getSubmissionId() + " " + title(outcome));
                }
            } else {
                failed++;
                if (failedLines.size() < MAX_ENTRIES) {
                    failedLines.add("- #" + outcome.getSubmissionId() + " " + title(outcome) + ": " + singleLine(outcome.getFailureReason()));
                }
            }
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("DEPOSIT_LOCATION", depositLocationName == null ? "" : depositLocationName);
        parameters.put("TOTAL_COUNT", String.valueOf(deposited + failed));
        parameters.put("DEPOSITED_COUNT", String.valueOf(deposited));
        parameters.put("FAILED_COUNT", String.valueOf(failed));
        parameters.put("FINISHED_AT", finishedAt.format(FINISHED_AT_FORMAT));
        parameters.put("DEPOSITED_LIST", listText(depositedLines, deposited, NO_DEPOSITS));
        parameters.put("FAILED_LIST", listText(failedLines, failed, NO_FAILURES));

        return new SummaryContent(parameters, depositedLines, failedLines);
    }

    private static String listText(List<String> lines, int total, String emptyText) {
        if (total == 0) {
            return emptyText;
        }
        List<String> text = new ArrayList<>(lines);
        if (total > lines.size()) {
            text.add("... and " + (total - lines.size()) + " more not listed");
        }
        return String.join("\n", text);
    }

    private static String title(DepositOutcome outcome) {
        return outcome.getTitle() == null ? NO_TITLE : singleLine(outcome.getTitle());
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ");
    }

}
