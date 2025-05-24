package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class ShareResponsePayload (
    val userName: String,
    val secretShare: ByteArray,
    val sender: String

) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(secretShare)
        payload += serializeVarLen(sender.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<ShareResponsePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ShareResponsePayload, Int> {
            var localOffset = offset

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize

            val (secretShareBytes, secretShareSize) = deserializeVarLen(buffer, localOffset)
            localOffset += secretShareSize
            val (senderBytes, senderSize) = deserializeVarLen(buffer, localOffset)
            localOffset += senderSize

            return Pair(
                ShareResponsePayload(
                    nameBytes.toString(Charsets.UTF_8), secretShareBytes, senderBytes.toString(Charsets.UTF_8)
                ),
                localOffset - offset
            )
        }
    }
}
