package com.github.quiltservertools.ledger.database

import com.github.quiltservertools.ledger.actions.ActionType
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

/** Canonical payload identity of a producer action; the format tag is part of the hash. */
internal object ProducerPayload {
    private const val FORMAT = "ledger-producer-payload-v1"

    fun hash(action: ActionType): String {
        val digest = MessageDigest.getInstance("SHA-256")

        // Length-prefixed fields, with null distinct from empty, so no two payloads share an encoding.
        fun field(value: String?) {
            if (value == null) {
                digest.update(0)
                return
            }
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(1)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        field(FORMAT)
        field(action.identifier)
        field(action.timestamp.epochSecond.toString())
        field(action.timestamp.nano.toString())
        field(action.world?.toString())
        field(action.pos.x.toString())
        field(action.pos.y.toString())
        field(action.pos.z.toString())
        field(action.objectIdentifier.toString())
        field(action.oldObjectIdentifier.toString())
        field(action.objectState)
        field(action.oldObjectState)
        field(action.sourceName)
        field(action.sourceProfile?.id?.toString())
        field(action.sourceProfile?.name)
        field(action.extraData)
        return HexFormat.of().formatHex(digest.digest())
    }
}
