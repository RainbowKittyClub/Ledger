package com.github.quiltservertools.ledger.api;

import com.github.quiltservertools.ledger.actions.ActionType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Actions from one producer operation, committed in one transaction with their receipt keys.
 * Each sequence is unique within (producer, operationId); a retry must carry identical actions.
 *
 * @param producer stable producer name, at most 30 characters
 * @param operationId producer operation identity
 * @param entries actions in strictly increasing sequence order
 */
public record ProducerBatch(String producer, UUID operationId, List<Entry> entries) {
    public static final int MAX_PRODUCER_LENGTH = 30;

    public ProducerBatch {
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(operationId, "operationId");
        entries = List.copyOf(entries);
        if (producer.isEmpty() || producer.length() > MAX_PRODUCER_LENGTH) {
            throw new IllegalArgumentException("Producer must be 1 to " + MAX_PRODUCER_LENGTH + " characters");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A producer batch needs at least one entry");
        }
        long previous = -1;
        for (Entry entry : entries) {
            if (entry.sequence() <= previous) {
                throw new IllegalArgumentException("Sequences must be nonnegative and strictly increasing");
            }
            previous = entry.sequence();
        }
    }

    /**
     * @param sequence producer-assigned, unique within the operation
     * @param action the action row to store
     */
    public record Entry(long sequence, ActionType action) {
        public Entry {
            Objects.requireNonNull(action, "action");
        }
    }
}
