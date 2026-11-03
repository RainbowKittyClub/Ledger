package com.github.quiltservertools.ledger.database

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.api.BatchAck
import com.github.quiltservertools.ledger.api.ProducerBatch
import com.github.quiltservertools.ledger.api.ProducerBatchException
import com.github.quiltservertools.ledger.api.ProducerBatchException.Reason
import com.github.quiltservertools.ledger.config.DatabaseSpec
import com.github.quiltservertools.ledger.registry.ActionRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_SOURCE_NAME_LENGTH = 30
private const val MAX_PLAYER_NAME_LENGTH = 16
private const val MAX_IDENTIFIER_LENGTH = 191

/**
 * Bounded, acknowledged producer batches. Admission is decided synchronously, so a full queue
 * fails the caller at once; one consumer commits each batch in its own transaction, in order.
 */
object ProducerBatchService {
    private class Pending(val batch: ProducerBatch, val future: CompletableFuture<BatchAck>)

    private val channel = Channel<Pending>(Channel.UNLIMITED)
    private val pendingActions = AtomicInteger()
    private val pendingBatches = AtomicInteger()

    @Volatile
    private var accepting = false
    private var job: Job? = null

    val pendingActionCount: Int get() = pendingActions.get()
    val pendingBatchCount: Int get() = pendingBatches.get()

    fun start() {
        job = Ledger.launch {
            for (pending in channel) process(pending)
        }
        accepting = true
    }

    fun submit(batch: ProducerBatch): CompletableFuture<BatchAck> {
        if (!accepting) return failed(Reason.STOPPED, "Ledger is not accepting producer batches")
        val size = batch.entries().size
        val maxBatch = batchLimit()
        if (size > maxBatch) return failed(Reason.INVALID, "batch has $size actions, over the limit of $maxBatch")
        for (entry in batch.entries()) {
            validate(entry.action())?.let { return failed(Reason.INVALID, "sequence ${entry.sequence()}: $it") }
        }
        if (!reserve(size)) {
            return failed(
                Reason.BACKPRESSURE,
                "producer queue is full (${pendingActions.get()} actions in ${pendingBatches.get()} batches)",
            )
        }
        val future = CompletableFuture<BatchAck>()
        if (channel.trySend(Pending(batch, future)).isFailure) {
            release(size)
            return failed(Reason.STOPPED, "Ledger is not accepting producer batches")
        }
        return future
    }

    /** Largest batch that can ever be admitted: a bigger one could never fit the pending limit. */
    fun batchLimit(): Int = minOf(
        Ledger.config[DatabaseSpec.producerMaxBatchActions],
        Ledger.config[DatabaseSpec.producerMaxPendingActions],
    )

    /** Null when the action could be stored now, otherwise why not; admission state counts too. */
    fun validate(action: ActionType): String? {
        val extra = action.extraData?.length ?: 0
        return when {
            !accepting -> "Ledger is not accepting producer batches"

            pendingActions.get() >= Ledger.config[DatabaseSpec.producerMaxPendingActions] ->
                "Ledger's producer queue is full"

            ActionRegistry.getType(action.identifier) == null -> "action type ${action.identifier} is not registered"

            action.isBlacklisted() -> "action ${action.identifier} is blacklisted by Ledger config"

            extra > MAX_EXTRA_DATA_CHARS -> "extra data is $extra characters, over $MAX_EXTRA_DATA_CHARS"

            action.sourceName.length > MAX_SOURCE_NAME_LENGTH -> "source name is over $MAX_SOURCE_NAME_LENGTH chars"

            (action.sourceProfile?.name?.length ?: 0) > MAX_PLAYER_NAME_LENGTH ->
                "player name is over $MAX_PLAYER_NAME_LENGTH characters"

            listOfNotNull(action.world, action.objectIdentifier, action.oldObjectIdentifier)
                .any { it.toString().length > MAX_IDENTIFIER_LENGTH } ->
                "an identifier is over $MAX_IDENTIFIER_LENGTH characters"

            else -> null
        }
    }

    /** Stop admitting and commit what is queued; Ledger's shutdown drain bounds the wait. */
    suspend fun drain() {
        accepting = false
        channel.close()
        job?.join()
    }

    /** After a timed-out drain: fail whatever is left, so no caller waits forever. */
    fun abandon() {
        accepting = false
        channel.close()
        job?.cancel()
        while (true) {
            val pending = channel.tryReceive().getOrNull() ?: break
            release(pending.batch.entries().size)
            pending.future.completeExceptionally(
                ProducerBatchException(Reason.STOPPED, "Ledger stopped before committing this batch"),
            )
        }
    }

    private suspend fun process(pending: Pending) {
        try {
            pending.future.complete(DatabaseManager.appendProducerBatch(pending.batch))
        } catch (e: ProducerBatchException) {
            pending.future.completeExceptionally(e)
        } catch (e: CancellationException) {
            pending.future.completeExceptionally(
                ProducerBatchException(Reason.STOPPED, "Ledger stopped while committing this batch", e),
            )
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            pending.future.completeExceptionally(
                ProducerBatchException(Reason.UNAVAILABLE, e.message ?: e.javaClass.simpleName, e),
            )
        } finally {
            release(pending.batch.entries().size)
        }
    }

    private fun reserve(size: Int): Boolean {
        val maxActions = Ledger.config[DatabaseSpec.producerMaxPendingActions]
        val maxBatches = Ledger.config[DatabaseSpec.producerMaxPendingBatches]
        while (true) {
            val actions = pendingActions.get()
            if (actions + size > maxActions) return false
            if (!pendingActions.compareAndSet(actions, actions + size)) continue
            if (pendingBatches.incrementAndGet() > maxBatches) {
                pendingBatches.decrementAndGet()
                pendingActions.addAndGet(-size)
                return false
            }
            return true
        }
    }

    private fun release(size: Int) {
        pendingActions.addAndGet(-size)
        pendingBatches.decrementAndGet()
    }

    private fun failed(reason: Reason, message: String): CompletableFuture<BatchAck> =
        CompletableFuture.failedFuture(ProducerBatchException(reason, message))
}
