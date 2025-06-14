package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.security.SecureRandom
import kotlin.math.sign

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    private var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null,
    var Identification_Value: String = "",
    val connected: MutableList<String> = mutableListOf(),
    var identified: Boolean = false,
    val n: Int = 3,
    val k: Int = 2
) : Participant(communicationProtocol, name, onDataChangeCallback) {

    lateinit var scheme: Scheme
    lateinit var wallet: Wallet
    val myShares: MutableList<Pair<String,ByteArray>> =  MutableList(n) { Pair("", ByteArray(0)) }

    init {
        communicationProtocol.participant = this
        this.group = group

        if (!runSetup) {
            generateKeyPair()
            wallet = Wallet(privateKey, publicKey, walletManager!!)
        }

        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }
    }

     suspend fun setup() {
         setUp()
         // Need private key from setup to generate a new wallet
         wallet = Wallet(privateKey, publicKey, walletManager!!)
     }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails =
            wallet!!.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet!!.doubleSpendEuro(randomizationElements, group, crs)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun connectToTTP(ttpName: String) {
        val communicationProtocol = communicationProtocol as IPV8CommunicationProtocol
        val address = communicationProtocol.addressBookManager.getAddressByName(ttpName)
        val connectedNames = connected.map { it }

        if ((address.type == Role.REG_TTP || address.type == Role.TTP) && address.name !in connectedNames) {
            // add element to connected TTP list
            connected.add(address.name)
            onDataChangeCallback?.invoke("connectedChange")
            if (connected.size >= n) {
                // if n connections, secret share
                scheme = Scheme(SecureRandom(), n, k)
                val parts =
                    scheme.split(Identification_Value.toByteArray(Charsets.UTF_8))
                val partialParts = parts.entries.take(n).associate { it.toPair() }
                val partsList = partialParts.values.toList()
                connected.sort() // sort alphabetically for recovery

                for (i in connected.indices) {
                    communicationProtocol.connect(name, partsList[i]!!, connected[i])
                    myShares[i] = Pair(connected[i], ByteArray(0)) // myShares contains the ttp names for easier reconstruction
                }

            }

        }
        return
    }

    fun recoverShare(ttpName: String): ByteArray {
//        Log.i("adr_recover","asking to recover my share. my private is $privateKey\nmy public is $publicKey")
        val signature = Schnorr.schnorrSignature(privateKey, (name + ":" + System.currentTimeMillis().toString()).toByteArray(Charsets.UTF_8), group)
        return communicationProtocol.requestShare(signature,name,ttpName)
    }

    fun withdrawDigitalEuro(bank: String): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()

        val bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)
        val blindSignature = communicationProtocol.requestBlindSignature(publicKey, bank, blindedChallenge.blindedChallenge)
        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())
        wallet.addToWallet(digitalEuro, firstT)
        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")
        return digitalEuro
    }

    fun getBalance(): Int {
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (transactionResult.valid) {
            wallet!!.addToWallet(transactionDetails, usedRandomness)
            onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override suspend fun reset() {
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()

        setUp()
    }
}
