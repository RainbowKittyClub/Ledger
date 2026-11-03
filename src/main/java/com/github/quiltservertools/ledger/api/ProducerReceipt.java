package com.github.quiltservertools.ledger.api;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One stored producer key.
 *
 * @param payloadHash canonical payload hash, including its format version
 * @param receiptId the batch receipt that committed it
 * @param actionId the action row, or null once purged
 */
public record ProducerReceipt(String producer, UUID operationId, long sequence, String payloadHash,
        long receiptId, @Nullable Integer actionId) {
}
