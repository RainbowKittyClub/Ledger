package com.github.quiltservertools.ledger.api;

/** Why a producer batch was not committed; nothing from a failed batch is stored. */
public final class ProducerBatchException extends RuntimeException {
    public enum Reason {
        /** Admission is full; retry later. */
        BACKPRESSURE,
        /** An action cannot be stored as given (size, registration, config blacklist). */
        INVALID,
        /** A stored sequence holds a different payload. */
        CONFLICT,
        /** The database failed after retries. */
        UNAVAILABLE,
        /** Ledger is not accepting producer batches (stopping or not started). */
        STOPPED
    }

    private final Reason reason;

    public ProducerBatchException(Reason reason, String message) {
        this(reason, message, null);
    }

    public ProducerBatchException(Reason reason, String message, Throwable cause) {
        super(reason + ": " + message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
