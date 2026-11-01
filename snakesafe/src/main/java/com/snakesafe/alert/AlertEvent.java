package com.snakesafe.alert;

/** Published inside the database transaction, delivered to sockets only after it commits. */
public record AlertEvent(String alertId, Long hospitalId, Kind kind) {

    public enum Kind {
        /** Moved to WAITING: the hospital hears about it for the first time. */
        SENT_TO_HOSPITAL,
        /** Any other change after the hospital knew about it. */
        UPDATED,
        /** Patient cancelled after the hospital was told. */
        CANCELLED_AFTER_SEND,
        /** Change the hospital does not need (e.g. cancelled inside the window). */
        PATIENT_ONLY
    }
}
