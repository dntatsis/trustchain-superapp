package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.ui.ParticipantHolder
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.min

class Bank(
    name: String,
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val depositedEuroManager: DepositedEuroManager = DepositedEuroManager(context, group),
    runSetup: Boolean = false,
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    private val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    val withdrawUserRandomness: HashMap<Element, Element> = hashMapOf()
    val depositedEuroLogger: ArrayList<Pair<String, Boolean>> = arrayListOf()

    init {
        communicationProtocol.participant = this
        this.group = group
        if (runSetup) generateKeyPair()
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
        //onDataChangeCallback?.invoke("A token was withdrawn by $userPublicKey")
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
            //onDataChangeCallback?.invoke("An euro was deposited successfully by $publicKeyUser")
            Log.i("adr deposit","Deposit was successful!")
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
                var dsResult: MutableMap<String, FraudControlReplyMessage> = mutableMapOf()

                if (!isAllRoles) {
                    dsResult = communicationProtocol.requestFraudControl(euroProof, depositProof) as MutableMap<String, FraudControlReplyMessage>
                } else {
                    dsResult = simulateFraudControl(euroProof,depositProof)
                  }

                // reconstruct secret from shares

                val sortedMessages = dsResult.entries.sortedBy { it.key }

                val partialPart: Map<Int, ByteArray> = sortedMessages.mapIndexed { index, message ->
                    (index + 1) to message.value.result
                }.toMap()

                val scheme = Scheme(SecureRandom(), User.maximum_shares, User.minimum_shares)

                val recovered = scheme.join(partialPart)
                val recoveredString = String(recovered, Charsets.UTF_8)

                Log.i("adr fraud control", "Recovered string: $recoveredString")

                if (dsResult.isNotEmpty()) {
                    depositedEuroLogger.add(Pair(euro.serialNumber, true))
                    // <Increase user balance here and penalize the fraudulent User>
                    depositedEuroManager.insertDigitalEuro(euro)
                    // onDataChangeCallback?.invoke(dsResult.toString())
                    return "Double spending detected, user secret: $recoveredString"
                }
            } catch (e: Exception) {
                depositedEuroLogger.add(Pair(euro.serialNumber, true))
                depositedEuroManager.insertDigitalEuro(euro)
                //onDataChangeCallback?.invoke("Noticed double spending but could not reach TTP")
                return "Found double spending proofs, but TTP is unreachable"
            }
        }
        depositedEuroLogger.add(Pair(euro.serialNumber, true))
        // <Increase user balance here>
        depositedEuroManager.insertDigitalEuro(euro)
        // onDataChangeCallback?.invoke("Noticed double spending but could not find a proof")
        return "Detected double spending but could not blame anyone"
    }

    fun simulateFraudControl(
        proof1: GrothSahaiProof,
        proof2: GrothSahaiProof

    ): MutableMap<String, FraudControlReplyMessage> {
        val dsResult: MutableMap<String, FraudControlReplyMessage> = mutableMapOf()

        val regttpresult = ParticipantHolder.regttp!!.getUserFromProofs(proof1, proof2)

        if (regttpresult == null){
            dsResult[ParticipantHolder.regttp!!.name] = FraudControlReplyMessage(ByteArray(0))

        }
        else{
            dsResult[ParticipantHolder.regttp!!.name] = FraudControlReplyMessage(regttpresult)

        }

        for (ttp in ParticipantHolder.ttp!!) {
            val result = ttp.getUserFromProofs(proof1, proof1)
            if (result == null){ // in case of null reply from TTP
                dsResult[ttp.name] = FraudControlReplyMessage(ByteArray(0))
            }
            else{
                dsResult[ttp.name] = FraudControlReplyMessage(result)
            }
        }
        return dsResult

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
            val retval = depositEuro(transactionDetails.digitalEuro, publicKeySender)
            return retval
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
