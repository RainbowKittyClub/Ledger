package com.github.quiltservertools.ledger.api;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Who is replaying, and how to ask whether they still may.
 *
 * @param replayId identifies this replay; its audit row carries it
 * @param operator the player running it, or null for the console
 * @param operatorName the operator's name, "Server" for the console
 * @param authorized whether the operator holds the replay permission now; ask before every write
 */
public record ReplayContext(UUID replayId, ReplayMode mode, @Nullable ServerPlayer operator, String operatorName,
        BooleanSupplier authorized) {
}
