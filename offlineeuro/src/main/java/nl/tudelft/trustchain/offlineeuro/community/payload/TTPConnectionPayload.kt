package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class TTPConnectionPayload(
    val userName: String,
    val secretShare: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(secretShare)
        return payload
    }

    companion object Deserializer : Deserializable<TTPConnectionPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TTPConnectionPayload, Int> {
            var localOffset = offset

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize

            val (secretShareBytes, secretShareSize) = deserializeVarLen(buffer, localOffset)
            localOffset += secretShareSize

            return Pair(
                TTPConnectionPayload(
                    nameBytes.toString(Charsets.UTF_8),
                    secretShareBytes
                ),
                localOffset - offset
            )
        }
    }
}
