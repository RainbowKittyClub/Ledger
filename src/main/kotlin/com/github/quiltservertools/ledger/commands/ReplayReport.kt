package com.github.quiltservertools.ledger.commands

import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.api.ReplayMode
import com.github.quiltservertools.ledger.api.ReplayOutcome
import com.github.quiltservertools.ledger.api.ReplayResult
import com.github.quiltservertools.ledger.database.ReplayService
import com.github.quiltservertools.ledger.utility.TextColorPallet
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletionException

/** Runs a replay for a command and reports, once flags and audit are committed, what became of each action. */
object ReplayReport {
    fun run(source: CommandSourceStack, params: ActionSearchParams, mode: ReplayMode): Int {
        ReplayService.replay(source, params, mode).whenComplete { result, failure ->
            source.server.execute { send(source, mode, result, failure) }
        }
        return 1
    }

    private fun send(source: CommandSourceStack, mode: ReplayMode, result: ReplayResult?, failure: Throwable?) {
        if (failure != null || result == null) {
            val cause = if (failure is CompletionException && failure.cause != null) failure.cause!! else failure
            source.sendFailure(Component.translatable("text.ledger.replay.failed", cause?.message ?: cause.toString()))
            return
        }
        if (result.outcomes.isEmpty()) {
            source.sendFailure(Component.translatable("error.ledger.command.no_results"))
            return
        }
        val finish = if (mode == ReplayMode.ROLLBACK) "text.ledger.rollback.finish" else "text.ledger.restore.finish"
        source.sendSuccess({ Component.translatable(finish).setStyle(TextColorPallet.primary) }, true)
        source.sendSuccess(
            {
                Component.translatable(
                    "text.ledger.replay.outcome",
                    result.count(ReplayOutcome.APPLIED),
                    result.count(ReplayOutcome.CONFLICT),
                    result.count(ReplayOutcome.DENIED),
                    result.count(ReplayOutcome.UNSUPPORTED),
                    result.count(ReplayOutcome.FAILED),
                ).setStyle(TextColorPallet.secondary)
            },
            true,
        )
    }
}
