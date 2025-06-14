package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import java.math.BigInteger
import kotlin.math.min

class Bank(
    name: String,
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val depositedEuroManager: DepositedEuroManager = DepositedEuroManager(context, group),
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    private val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    val withdrawUserRandomness: HashMap<Element, Element> = hashMapOf()
    val depositedEuroLogger: ArrayList<Pair<String, Boolean>> = arrayListOf()

    init {
        communicationProtocol.participant = this
        this.group = group
        if (!runSetup) generateKeyPair()
    }

    fun getBlindSignatureRandomness(userPublicKey: Element): Element {
        if (withdrawUserRandomness.containsKey(userPublicKey)) {
            val randomness = withdrawUserRandomness[userPublicKey]!!
            return group.g.powZn(randomness)
        }
        val randomness = group.getRandomZr()
        withdrawUserRandomness[userPublicKey] = randomness
        return group.g.powZn(randomness)
    }

    fun createBlindSignature(
        challenge: BigInteger,
        userPublicKey: Element
    ): BigInteger {
        val k =
            lookUp(userPublicKey)
                ?: return BigInteger.ZERO
        remove(userPublicKey)

        onDataChangeCallback?.invoke("A token was withdrawn by $userPublicKey")
        // <Subtract balance here>
        return Schnorr.signBlindedChallenge(k, challenge, privateKey)
    }

    private fun lookUp(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return element.value
            }
        }

        return null
    }

    private fun remove(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return withdrawUserRandomness.remove(key)
            }
        }

        return null
    }

    private fun depositEuro(
        euro: DigitalEuro,
        publicKeyUser: Element
    ): String {
        val duplicateEuros = depositedEuroManager.getDigitalEurosByDescriptor(euro)

        if (duplicateEuros.isEmpty()) {
            depositedEuroLogger.add(Pair(euro.serialNumber, false))
            depositedEuroManager.insertDigitalEuro(euro)
            onDataChangeCallback?.invoke("An euro was deposited successfully by $publicKeyUser")
            return "Deposit was successful!"
        }

        var maxFirstDifferenceIndex = -1
        var doubleSpendEuro: DigitalEuro? = null
        for (duplicateEuro in duplicateEuros) {
            // Loop over the proofs to find the double spending
            val euroProofs = euro.proofs
            val duplicateEuroProofs = duplicateEuro.proofs

            for (i in 0 until min(euroProofs.size, duplicateEuroProofs.size)) {
                if (euroProofs[i] == duplicateEuroProofs[i]) {
                    continue
                } else if (i > maxFirstDifferenceIndex) {
                    maxFirstDifferenceIndex = i
                    doubleSpendEuro = duplicateEuro
                    break
                }
            }
        }

        if (doubleSpendEuro != null) {
            val euroProof = euro.proofs[maxFirstDifferenceIndex]
            val depositProof = doubleSpendEuro.proofs[maxFirstDifferenceIndex]
            try {
                val dsResult =
                    communicationProtocol.requestFraudControl(euroProof, depositProof)

                if (dsResult != "") {
                    depositedEuroLogger.add(Pair(euro.serialNumber, true))
                    // <Increase user balance here and penalize the fraudulent User>
                    depositedEuroManager.insertDigitalEuro(euro)
                    onDataChangeCallback?.invoke(dsResult)
                    return dsResult
                }
            } catch (e: Exception) {
                depositedEuroLogger.add(Pair(euro.serialNumber, true))
                depositedEuroManager.insertDigitalEuro(euro)
                onDataChangeCallback?.invoke("Noticed double spending but could not reach TTP")
                return "Found double spending proofs, but TTP is unreachable"
            }
        }
        depositedEuroLogger.add(Pair(euro.serialNumber, true))
        // <Increase user balance here>
        depositedEuroManager.insertDigitalEuro(euro)
        onDataChangeCallback?.invoke("Noticed double spending but could not find a proof")
        return "Detected double spending but could not blame anyone"
    }

    fun getDepositedTokens(): List<DigitalEuro> {
        return depositedEuros
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)
        if (transactionResult.valid) {
            val digitalEuro = transactionDetails.digitalEuro
            digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)
            return depositEuro(transactionDetails.digitalEuro, publicKeySender)
        }

        return transactionResult.description
    }

    override suspend fun reset() {
        randomizationElementMap.clear()
        withdrawUserRandomness.clear()
        depositedEuroManager.clearDepositedEuros()
        setUp()
    }
}
