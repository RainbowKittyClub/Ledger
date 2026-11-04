package com.github.quiltservertools.ledger.api;

/** Which way a replay runs: rollback undoes not-yet-rolled-back actions, restore redoes rolled-back ones. */
public enum ReplayMode {
    ROLLBACK,
    RESTORE
}
