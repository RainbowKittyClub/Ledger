package com.github.quiltservertools.ledger.api;

import java.util.List;
import java.util.UUID;

/**
 * Proof that a producer batch's action rows and receipt keys are committed.
 *
 * @param receiptId the committed batch receipt; for a batch that was entirely already stored, the
 *        earliest receipt holding one of its sequences
 * @param accepted sequences committed by this call
 * @param duplicates sequences already stored with an identical payload, not stored again
 */
public record BatchAck(String producer, UUID operationId, long receiptId, List<Long> accepted,
        List<Long> duplicates) {
    public BatchAck {
        accepted = List.copyOf(accepted);
        duplicates = List.copyOf(duplicates);
    }
}
