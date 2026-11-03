package com.github.quiltservertools.ledger.api

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.actionutils.SearchResults
import com.github.quiltservertools.ledger.database.ActionQueueService
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.database.ProducerBatchService
import kotlinx.coroutines.future.future
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal object LedgerApiImpl : LedgerApi {
    override fun searchActions(params: ActionSearchParams, page: Int): CompletableFuture<SearchResults> =
        Ledger.future { DatabaseManager.searchActions(params, page) }

    override fun countActions(params: ActionSearchParams): CompletableFuture<Long> =
        Ledger.future { DatabaseManager.countActions(params) }

    override fun rollbackActions(params: ActionSearchParams): CompletableFuture<List<ActionType>> =
        Ledger.future { DatabaseManager.rollbackActions(params) }

    override fun restoreActions(params: ActionSearchParams): CompletableFuture<List<ActionType>> =
        Ledger.future { DatabaseManager.restoreActions(params) }

    override fun logAction(action: ActionType) {
        ActionQueueService.addToQueue(action)
    }

    override fun appendBatch(batch: ProducerBatch): CompletableFuture<BatchAck> = ProducerBatchService.submit(batch)

    override fun validateProducerAction(action: ActionType): String? = ProducerBatchService.validate(action)

    override fun producerBatchLimit(): Int = ProducerBatchService.batchLimit()

    override fun producerReceipts(producer: String, operationId: UUID): CompletableFuture<List<ProducerReceipt>> =
        Ledger.future { DatabaseManager.producerReceipts(producer, operationId) }
}
