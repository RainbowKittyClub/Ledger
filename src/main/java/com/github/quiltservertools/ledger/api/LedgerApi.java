package com.github.quiltservertools.ledger.api;

import com.github.quiltservertools.ledger.Ledger;
import com.github.quiltservertools.ledger.actions.ActionType;
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams;
import com.github.quiltservertools.ledger.actionutils.SearchResults;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

public interface LedgerApi {
    /**
     * The installed Ledger's API, reachable without Kotlin classes on the caller's compile classpath.
     * @return The API
     */
    static LedgerApi get() {
        return Ledger.getApi();
    }

    /**
     * Executes a search on the database
     * @param params The search parameters to filter results
     * @param page The page of results you want
     * @return A list of the results on the page along with the current page and total pages
     */
    CompletableFuture<SearchResults> searchActions(ActionSearchParams params, int page);

    /**
     * Counts <b>all</b> matching actions matching the parameters
     * @param params The search parameters to filter results
     * @return The number of matching actions
     */
    CompletableFuture<Long> countActions(ActionSearchParams params);

    /**
     * Executes a rollback for the matching actions
     * @param params The search parameters to filter results
     * @return A list of the actions that failed to rollback
     */
    CompletableFuture<List<ActionType>> rollbackActions(ActionSearchParams params);

    /**
     * Executes a restore (undoes a rollback) for the matching actions
     * @param params The search parameters to filter results
     * @return A list of the actions that failed to restore
     */
    CompletableFuture<List<ActionType>> restoreActions(ActionSearchParams params);

    /**
     * Logs an action to the database
     * @param action The action to log
     */
    void logAction(ActionType action);

    /** Version of the producer batch contract below; absent on Ledger builds without it. */
    int PRODUCER_API_VERSION = 1;

    /**
     * Durably commits a producer batch: its action rows and its (producer, operation, sequence)
     * keys share one transaction. Admission is bounded; when full the future fails at once with
     * {@link ProducerBatchException.Reason#BACKPRESSURE}. An identical retry stores nothing new and
     * reports the sequences as duplicates; a stored sequence with a different payload fails with
     * {@link ProducerBatchException.Reason#CONFLICT}.
     * @param batch The batch to commit
     * @return Completes only after the SQL transaction commits
     */
    CompletableFuture<BatchAck> appendBatch(ProducerBatch batch);

    /**
     * Checks whether an action could be stored by {@link #appendBatch} now, without storing it:
     * the action itself, and whether Ledger is accepting and has queue room. Producers call this
     * before changing the world, so an unstorable action is refused early.
     * @param action The action to check
     * @return null when storable, otherwise the reason it would be refused
     */
    @Nullable String validateProducerAction(ActionType action);

    /**
     * The most actions one producer batch may hold; a larger batch fails as invalid.
     * @return The batch size limit
     */
    int producerBatchLimit();

    /**
     * Reads stored producer keys for one operation, for restart and retry decisions.
     * @param producer The producer name
     * @param operationId The producer operation
     * @return Receipts in sequence order
     */
    CompletableFuture<List<ProducerReceipt>> producerReceipts(String producer, UUID operationId);
}
