package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer

class ShareRequestPayload (
    val signature: SchnorrSignature,
    val name: String
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(SchnorrSignatureSerializer.serializeSchnorrSignature(signature))
        payload += serializeVarLen(name.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<ShareRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ShareRequestPayload, Int> {
            var localOffset = offset

            val (signatureBytes, signatureSize) = deserializeVarLen(buffer, localOffset)
            localOffset += signatureSize

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize

            return Pair(
                ShareRequestPayload(
                    SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(signatureBytes)!!,
                    nameBytes.toString(Charsets.UTF_8)
                ),
                localOffset - offset
            )
        }
    }
}
