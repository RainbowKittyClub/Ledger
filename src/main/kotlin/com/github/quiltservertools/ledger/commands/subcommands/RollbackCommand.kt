package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.api.ReplayMode
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.commands.ReplayReport
import com.github.quiltservertools.ledger.commands.arguments.SearchParamArgument
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.LiteralNode
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands

object RollbackCommand : BuildableCommand {
    override fun build(): LiteralNode = Commands.literal("rollback")
        .requires(Permissions.require("ledger.commands.rollback", CommandConsts.PERMISSION_LEVEL))
        .then(
            SearchParamArgument.argument("params")
                .executes { rollback(it, SearchParamArgument.get(it, "params")) },
        )
        .build()

    fun rollback(context: Context, params: ActionSearchParams): Int {
        params.ensureSpecific()
        return ReplayReport.run(context.source, params, ReplayMode.ROLLBACK)
    }
}
