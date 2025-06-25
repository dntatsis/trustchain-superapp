package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes

class BilinearGroupCRSPayload(
    val bilinearGroupElements: BilinearGroupElementsBytes,
    val crsFirst: CRSBytes,
    val crsSecond: CRSBytes,
    val ttpPublicKey: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(bilinearGroupElements.g)
        payload += serializeVarLen(bilinearGroupElements.h)
        payload += serializeVarLen(bilinearGroupElements.gt)

        payload += serializeVarLen(crsFirst.g)
        payload += serializeVarLen(crsFirst.u)
        payload += serializeVarLen(crsFirst.gPrime)
        payload += serializeVarLen(crsFirst.uPrime)

        payload += serializeVarLen(crsFirst.h)
        payload += serializeVarLen(crsFirst.v)
        payload += serializeVarLen(crsFirst.hPrime)
        payload += serializeVarLen(crsFirst.vPrime)

        payload += serializeVarLen(crsSecond.g)
        payload += serializeVarLen(crsSecond.u)
        payload += serializeVarLen(crsSecond.gPrime)
        payload += serializeVarLen(crsSecond.uPrime)

        payload += serializeVarLen(crsSecond.h)
        payload += serializeVarLen(crsSecond.v)
        payload += serializeVarLen(crsSecond.hPrime)
        payload += serializeVarLen(crsSecond.vPrime)
        payload += serializeVarLen(ttpPublicKey)

        return payload
    }

    companion object Deserializer : Deserializable<BilinearGroupCRSPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BilinearGroupCRSPayload, Int> {
            var localOffset = offset

            val (groupG, groupGSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupGSize

            val (groupH, groupHSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupHSize

            val (groupGt, groupGtSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupGtSize

            val (crsFirstG, crsFirstGSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstGSize

            val (crsFirstU, crsFirstUSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstUSize

            val (crsFirstGPrime, crsFirstGPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstGPrimeSize

            val (crsFirstUPrime, crsFirstUPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstUPrimeSize

            val (crsFirstH, crsFirstHSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstHSize

            val (crsFirstV, crsFirstVSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstVSize

            val (crsFirstHPrime, crsFirstHPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstHPrimeSize

            val (crsFirstVPrime, crsFirstVPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsFirstVPrimeSize

            val (crsSecondG, crsSecondGSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondGSize

            val (crsSecondU, crsSecondUSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondUSize

            val (crsSecondGPrime, crsSecondGPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondGPrimeSize

            val (crsSecondUPrime, crsSecondUPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondUPrimeSize

            val (crsSecondH, crsSecondHSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondHSize

            val (crsSecondV, crsSecondVSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondVSize

            val (crsSecondHPrime, crsSecondHPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondHPrimeSize

            val (crsSecondVPrime, crsSecondVPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsSecondVPrimeSize

            val (ttpPubKeyBytes, ttpPubKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += ttpPubKeySize

            val groupElementsBytes = BilinearGroupElementsBytes(groupG, groupH, groupGt)
            val crsFirstBytes =
                CRSBytes(
                    crsFirstG,
                    crsFirstU,
                    crsFirstGPrime,
                    crsFirstUPrime,
                    crsFirstH,
                    crsFirstV,
                    crsFirstHPrime,
                    crsFirstVPrime
                )
            val crsSecondBytes =
                CRSBytes(
                    crsSecondG,
                    crsSecondU,
                    crsSecondGPrime,
                    crsSecondUPrime,
                    crsSecondH,
                    crsSecondV,
                    crsSecondHPrime,
                    crsSecondVPrime
                )

            return Pair(
                BilinearGroupCRSPayload(groupElementsBytes, crsFirstBytes,crsSecondBytes, ttpPubKeyBytes),
                localOffset - offset
            )
        }
    }
}
