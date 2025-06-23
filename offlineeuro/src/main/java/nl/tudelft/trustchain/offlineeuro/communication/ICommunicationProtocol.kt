package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import java.math.BigInteger

interface ICommunicationProtocol {
    var participant: Participant

    suspend fun getGroupDescriptionAndCRS()

    fun requestShare(
        signature: SchnorrSignature,
        name: String,
        ttpname: String
    ): ByteArray

    fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String
    )
    fun connect(
        userName: String,
        secretShare: ByteArray,
        nameTTP: String
    )

    fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element

    fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger

    fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements

    fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String

    fun requestFraudControl(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof,
//        nameTTP: String
    ): String

    fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element
}
