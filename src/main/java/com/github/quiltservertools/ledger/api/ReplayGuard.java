package com.github.quiltservertools.ledger.api;

import com.github.quiltservertools.ledger.actions.ActionType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Performs every world change of a replay in place of each action's own rollback or restore, so a
 * protection or audit layer sees all of them. While one is registered Ledger changes the world only
 * through it, for actions of any producer.
 */
public interface ReplayGuard {
    /**
     * Called on the server thread with the selected actions in Ledger's order (newest first for a
     * rollback, oldest first for a restore). Changes the world for those it can and reports each.
     * @param actions the actions to replay
     * @param context the operator and mode
     * @return an outcome for every action id; a missing id counts as {@link ReplayOutcome#FAILED}
     */
    CompletableFuture<Map<Integer, ReplayOutcome>> replay(List<ActionType> actions, ReplayContext context);
}
