package com.github.quiltservertools.ledger.database

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actions.ReplayActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.api.ReplayContext
import com.github.quiltservertools.ledger.api.ReplayGuard
import com.github.quiltservertools.ledger.api.ReplayMode
import com.github.quiltservertools.ledger.api.ReplayOutcome
import com.github.quiltservertools.ledger.api.ReplayResult
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.logWarn
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.future.future
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

/**
 * The one replay path behind the rollback and restore commands, their preview and the API. The
 * world changes on the server thread, through the registered guard when there is one; then the
 * flags of applied actions and one audit row for the replay commit in a single transaction.
 */
object ReplayService {
    private const val PERMISSION = "ledger.commands.rollback"
    private const val FORMAT = "ledger-replay-v1"

    // Ids listed per replay before the audit row lists only counts; keeps it under the column limit.
    private const val AUDIT_ID_BUDGET = 2_000
    private val REPLAY_OBJECT: Identifier = Identifier.parse("ledger:replay")

    @Volatile
    private var guard: ReplayGuard? = null

    @Synchronized
    fun register(guard: ReplayGuard) {
        check(this.guard == null) { "A replay guard is already registered" }
        this.guard = guard
    }

    @Synchronized
    fun unregister(guard: ReplayGuard) {
        if (this.guard === guard) this.guard = null
    }

    fun replay(
        source: CommandSourceStack,
        params: ActionSearchParams,
        mode: ReplayMode,
    ): CompletableFuture<ReplayResult> {
        val server = source.server
        val context = ReplayContext(UUID.randomUUID(), mode, source.player, source.textName, authorized(source))
        return Ledger.future {
            val selected = when (mode) {
                ReplayMode.ROLLBACK -> DatabaseManager.selectRollback(params)
                ReplayMode.RESTORE -> DatabaseManager.selectRestore(params)
            }
            selected.filter { it.identifier != ReplayActionType.IDENTIFIER }
        }.thenComposeAsync({ actions -> changeWorld(server, actions, context).thenApply { actions to it } }, server)
            .thenCompose { (actions, outcomes) ->
                Ledger.future {
                    // A replay that selected nothing changed nothing and leaves no audit row.
                    if (actions.isNotEmpty()) {
                        val applied = outcomes.filterValues { it == ReplayOutcome.APPLIED }.keys
                        DatabaseManager.recordReplay(mode, applied, audit(source, context, actions, outcomes))
                    }
                    ReplayResult(context.replayId, mode, outcomes)
                }
            }
    }

    // Server thread. Every selected action gets an outcome; anything unreported counts as failed.
    private fun changeWorld(
        server: MinecraftServer,
        actions: List<ActionType>,
        context: ReplayContext,
    ): CompletableFuture<Map<Int, ReplayOutcome>> {
        val current = guard
        val reported = if (current != null) {
            // A guard that throws has made no promise about the world; nothing gets marked.
            try {
                current.replay(actions, context)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                CompletableFuture.failedFuture(failure)
            }
        } else {
            CompletableFuture.completedFuture(actions.associate { it.id to replayOwn(server, it, context) })
        }
        return reported.handle { outcomes, failure ->
            if (failure != null) logWarn("Replay ${context.replayId} guard failed; nothing is marked: $failure")
            actions.associate { it.id to (outcomes?.get(it.id) ?: ReplayOutcome.FAILED) }
        }
    }

    // Without a guard each action changes the world itself, as Ledger always has.
    private fun replayOwn(server: MinecraftServer, action: ActionType, context: ReplayContext): ReplayOutcome {
        if (!context.authorized.asBoolean) return ReplayOutcome.DENIED
        return try {
            val done = when (context.mode) {
                ReplayMode.ROLLBACK -> action.rollback(server)
                ReplayMode.RESTORE -> action.restore(server)
            }
            if (done) ReplayOutcome.APPLIED else ReplayOutcome.FAILED
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            logWarn("Replay of action ${action.id} failed: $failure")
            ReplayOutcome.FAILED
        }
    }

    // The console holds every permission; a player must still be online and still hold it.
    private fun authorized(source: CommandSourceStack): BooleanSupplier {
        val player = source.player ?: return BooleanSupplier { true }
        val server = source.server
        return BooleanSupplier {
            val online = server.playerList.getPlayer(player.uuid)
            online != null && !online.hasDisconnected() &&
                Permissions.check(online, PERMISSION, CommandConsts.PERMISSION_LEVEL)
        }
    }

    private fun audit(
        source: CommandSourceStack,
        context: ReplayContext,
        actions: List<ActionType>,
        outcomes: Map<Int, ReplayOutcome>,
    ): ActionType {
        val extra = JsonObject()
        extra.addProperty("format", FORMAT)
        extra.addProperty("replay", context.replayId.toString())
        extra.addProperty("mode", context.mode.name)
        extra.addProperty("operator", context.operatorName)
        extra.addProperty("selected", actions.size)
        val counts = JsonObject()
        val ids = JsonObject()
        var listed = 0
        for (outcome in ReplayOutcome.values()) {
            val matching = actions.map { it.id }.filter { outcomes[it] == outcome }
            counts.addProperty(outcome.name, matching.size)
            val array = JsonArray()
            for (id in matching) {
                if (listed++ >= AUDIT_ID_BUDGET) break
                array.add(id)
            }
            ids.add(outcome.name, array)
        }
        extra.add("counts", counts)
        extra.add("actions", ids)
        extra.addProperty("truncated", listed > AUDIT_ID_BUDGET)
        val replay = ReplayActionType()
        replay.timestamp = Instant.now()
        replay.pos = BlockPos.containing(source.position)
        replay.world = source.level.dimension().identifier()
        replay.objectIdentifier = REPLAY_OBJECT
        replay.oldObjectIdentifier = REPLAY_OBJECT
        replay.sourceName = if (context.operator == null) "console" else "player"
        replay.sourceProfile = context.operator?.nameAndId()
        replay.extraData = extra.toString()
        return replay
    }
}
