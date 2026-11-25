package org.tdl.vireo.model.batch;

/**
 * Whether the batch deposit summary email was sent, and if not, why.
 */
public enum SummaryEmailResult {

    SENT,

    NO_ADDRESS,

    NO_TEMPLATE,

    /** Building or sending the email threw. */
    SEND_FAILED

}
