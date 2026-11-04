package com.github.quiltservertools.ledger.api;

import java.util.Map;
import java.util.UUID;

/**
 * A finished replay. Returned only after the flags of applied actions and the replay's own audit
 * row are committed together.
 *
 * @param outcomes each selected action's outcome by action id
 */
public record ReplayResult(UUID replayId, ReplayMode mode, Map<Integer, ReplayOutcome> outcomes) {
    /** Actions with the given outcome. */
    public long count(ReplayOutcome outcome) {
        return outcomes.values().stream().filter(outcome::equals).count();
    }
}
