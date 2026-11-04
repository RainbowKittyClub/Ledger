package com.github.quiltservertools.ledger.actions

/**
 * One row per rollback or restore: who ran it and what became of each selected action, in its
 * extra data. It changes nothing itself, so replays never select it.
 */
class ReplayActionType : AbstractActionType() {
    override val identifier = IDENTIFIER

    override fun getTranslationType() = "replay"

    companion object {
        const val IDENTIFIER = "ledger-replay"
    }
}
