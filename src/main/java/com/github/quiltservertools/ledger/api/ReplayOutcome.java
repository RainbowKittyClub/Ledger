package com.github.quiltservertools.ledger.api;

/** What became of one action in a replay. Only {@link #APPLIED} changes its rolled-back flag. */
public enum ReplayOutcome {
    /** The world now holds the replayed state. */
    APPLIED,
    /** The world no longer holds the state the action left, so a later change would be overwritten. */
    CONFLICT,
    /** The current operator may not make this change now. */
    DENIED,
    /** No safe way to replay this kind of action is known. */
    UNSUPPORTED,
    /** The replay was attempted and did not finish. */
    FAILED
}
