package org.tdl.vireo.model.batch;

/**
 * Whether the batch deposit summary email was sent, and if not, why.
 */
public enum SummaryEmailResult {

    SENT,

    /** The reviewer has no email address. */
    NO_ADDRESS,

    /** The summary email template is not installed. */
    NO_TEMPLATE,

    /** Building or sending the email threw. */
    SEND_FAILED

}
