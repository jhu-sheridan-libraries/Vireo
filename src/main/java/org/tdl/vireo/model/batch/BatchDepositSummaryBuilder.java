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

    public static final String NO_NOT_RECORDED = "No submissions were left unrecorded.";

    public static final String NO_DEPOSIT_URL = "(no deposit URL returned)";

    /** Marks a deposited-but-not-recorded entry when it is listed among failed deposits (old template). */
    public static final String NOT_RECORDED_MARKER = "Deposited, not recorded in Vireo (do not retry)";

    private static final DateTimeFormatter FINISHED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public SummaryContent build(String depositLocationName, List<DepositOutcome> outcomes, ZonedDateTime finishedAt) {
        return build(depositLocationName, outcomes, finishedAt, false);
    }

    /**
     * @param legacyTemplate true when the template has no NOT_RECORDED_LIST placeholder. Deposited but
     *                       not recorded entries are then also listed in FAILED_LIST, marked as such,
     *                       and counted in FAILED_COUNT, so they still reach the reviewer.
     */
    public SummaryContent build(String depositLocationName, List<DepositOutcome> outcomes, ZonedDateTime finishedAt, boolean legacyTemplate) {
        return build(depositLocationName, outcomes, finishedAt, legacyTemplate, null);
    }

    /**
     * @param stop when not null, the run stopped early. A note saying so is put first in
     *             FAILED_LIST. It is not an entry: it is not counted and not subject to
     *             {@link #MAX_ENTRIES}, so it shows even when the failed list is truncated.
     */
    public SummaryContent build(String depositLocationName, List<DepositOutcome> outcomes, ZonedDateTime finishedAt, boolean legacyTemplate, BatchStop stop) {
        List<DepositOutcome> all = outcomes == null ? new ArrayList<>() : outcomes;

        List<String> depositedLines = new ArrayList<>();
        List<String> failedLines = new ArrayList<>();
        List<String> notRecordedLines = new ArrayList<>();
        int deposited = 0;
        int failed = 0;
        int notRecorded = 0;

        for (DepositOutcome outcome : all) {
            switch (outcome.getStatus()) {
            case DEPOSITED:
                deposited++;
                addCapped(depositedLines, "- #" + outcome.getSubmissionId() + " " + title(outcome));
                break;
            case NOT_RECORDED:
                notRecorded++;
                addCapped(notRecordedLines, "- #" + outcome.getSubmissionId() + " " + title(outcome) + " (" + depositURL(outcome) + "): " + singleLine(outcome.getFailureReason()));
                if (legacyTemplate) {
                    addCapped(failedLines, "- #" + outcome.getSubmissionId() + " " + title(outcome) + " (" + depositURL(outcome) + "): " + NOT_RECORDED_MARKER + ": " + singleLine(outcome.getFailureReason()));
                }
                break;
            case FAILED:
            default:
                failed++;
                addCapped(failedLines, "- #" + outcome.getSubmissionId() + " " + title(outcome) + ": " + singleLine(outcome.getFailureReason()));
                break;
            }
        }

        int failedShown = legacyTemplate ? failed + notRecorded : failed;

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("DEPOSIT_LOCATION", singleLine(depositLocationName));
        parameters.put("TOTAL_COUNT", String.valueOf(deposited + failed + notRecorded));
        parameters.put("DEPOSITED_COUNT", String.valueOf(deposited));
        parameters.put("FAILED_COUNT", String.valueOf(failedShown));
        parameters.put("NOT_RECORDED_COUNT", String.valueOf(notRecorded));
        parameters.put("FINISHED_AT", finishedAt.format(FINISHED_AT_FORMAT));
        parameters.put("NOT_RECORDED_LIST", listText(notRecordedLines, notRecorded, NO_NOT_RECORDED));
        parameters.put("DEPOSITED_LIST", listText(depositedLines, deposited, NO_DEPOSITS));
        String failedList = listText(failedLines, failedShown, NO_FAILURES);
        if (stop != null) {
            failedList = stoppedNote(stop) + "\n" + failedList;
        }
        parameters.put("FAILED_LIST", failedList);

        return new SummaryContent(parameters, depositedLines, failedLines, notRecordedLines);
    }

    /** The reason given to each submission the run did not reach after stopping. */
    public static String notAttemptedReason(long stoppedOnSubmissionId) {
        return "Not attempted: the batch stopped after an internal error on submission #" + stoppedOnSubmissionId;
    }

    static String stoppedNote(BatchStop stop) {
        String start = "The batch stopped early after an internal error on submission #" + stop.getSubmissionId() + ". ";
        int notAttempted = stop.getNotAttempted();
        if (notAttempted == 0) {
            return start + "Every other submission had already been attempted.";
        }
        if (notAttempted == 1) {
            return start + "1 submission was not attempted; it is listed below as failed and can be retried.";
        }
        return start + notAttempted + " submissions were not attempted; they are listed below as failed and can be retried.";
    }

    private static void addCapped(List<String> lines, String line) {
        if (lines.size() < MAX_ENTRIES) {
            lines.add(line);
        }
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

    private static String depositURL(DepositOutcome outcome) {
        String url = outcome.getDepositURL();
        return url == null || url.trim().isEmpty() ? NO_DEPOSIT_URL : singleLine(url);
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ");
    }

}
