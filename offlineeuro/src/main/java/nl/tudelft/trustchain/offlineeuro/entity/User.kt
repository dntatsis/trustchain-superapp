package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.ui.ParticipantHolder
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.sign

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = false,
    onDataChangeCallback: ((String?) -> Unit)? = null,
    var Identification_Value: String = "",
    val connected: MutableList<String> = mutableListOf(),
    var identified: Boolean = false,

) : Participant(communicationProtocol, name, onDataChangeCallback) {
    companion object { // Change N, K values here
        const val maximum_shares = 3
        const val minimum_shares = 2
    }
    lateinit var scheme: Scheme
    lateinit var wallet: Wallet
    val myShares: MutableList<Pair<String,ByteArray>> =  MutableList(maximum_shares) { Pair("", ByteArray(0)) }
        init {
        communicationProtocol.participant = this
        this.group = group


            // TODO: Fix setup here
        if (runSetup) generateKeyPair()

        if (walletManager == null) {
            walletManager = WalletManager(context, group,"wallet_$name")
        }
    }

     suspend fun setup() {
         setUp()
         // Need private key from setup to generate a new wallet
         wallet = Wallet(privateKey, publicKey, walletManager!!)
     }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = getRandomizationElements(nameReceiver)
        val transactionDetails = wallet.spendEuro(randomizationElements, group, crs)
            ?: run {
                handleTransactionFailure(nameReceiver)
                throw Exception("No euro to spend")
            }

        val result = sendTransactionDetails(nameReceiver, transactionDetails)
        if(result == "Valid transaction"){
            onDataChangeCallback?.invoke("Successful transaction")
        }
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = getRandomizationElements(nameReceiver)
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs)
            ?: run {
                handleTransactionFailure(nameReceiver)
                throw Exception("No euro to send")
            }

        val result = sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun getRandomizationElements(receiverName: String)
    :RandomizationElements
    {
        if (!isAllRoles){
            return communicationProtocol.requestTransactionRandomness(receiverName, group) // message exchange 1
        }

        val sent_pk = publicKey.toBytes()

        val combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
        val index = combinedUserAndBank!!.indexOfFirst {it!!.name == receiverName}

        if (index != -1){
            val publicKeyDec = combinedUserAndBank[index]!!.group.gElementFromBytes(sent_pk)

            val randomizationElementsOther = combinedUserAndBank[index]!!.generateRandomizationElements(publicKeyDec)
            val randomizationElementBytes = randomizationElementsOther.toRandomizationElementsBytes()
            val randomizationElements = randomizationElementBytes.toRandomizationElements(group)
            return randomizationElements
        }
        throw Exception("Recipient not found in ParticipantHolder")

    }

    fun handleTransactionFailure(nameReceiver: String) {

        val combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
        val index = combinedUserAndBank!!.indexOfFirst {it!!.name == nameReceiver}

        combinedUserAndBank[index]!!.removeRandomness(publicKey) // remove randomness before throwing exception, else further sends fail
        throw Exception("No euro to spend")

    }

    fun sendTransactionDetails(nameReceiver: String, transactionDetails: TransactionDetails)
    :String {

        lateinit var result:String
        if (!isAllRoles) {
            return communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails) // message exchange 2
        }
        var transactionDetailsToB = transactionDetails.toTransactionDetailsBytes()
        var sentPK = publicKey.toBytes()

        var combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
        var index = combinedUserAndBank!!.indexOfFirst {it!!.name == nameReceiver}

        if (index != -1) {

            val groupRecv = combinedUserAndBank[index]!!.group

            val publicKeyRecv = groupRecv.gElementFromBytes(sentPK)

            val transactionDetailsRecv = transactionDetailsToB.toTransactionDetails(groupRecv)

            val transactionResult = combinedUserAndBank[index]!!.onReceivedTransaction(transactionDetailsRecv, ParticipantHolder.bank!!.publicKey, publicKeyRecv)

            result = transactionResult
            onDataChangeCallback?.invoke(result)
            return result

        }
        throw Exception("Recipient not found in ParticipantHolder")


    }

    fun connectToTTP(ttpName: String) {
        val communicationProtocol = communicationProtocol as IPV8CommunicationProtocol
        val address = communicationProtocol.addressBookManager.getAddressByName(ttpName)
        val connectedNames = connected.map { it }

        if ((address.type == Role.REG_TTP || address.type == Role.TTP) && address.name !in connectedNames) {
            // add element to connected TTP list
            connected.add(address.name)
            onDataChangeCallback?.invoke("connectedChange")
            if (connected.size >= maximum_shares) {
                // if n connections, secret share
                scheme = Scheme(SecureRandom(), maximum_shares, minimum_shares)
                val parts =
                    scheme.split(Identification_Value.toByteArray(Charsets.UTF_8))
                val partialParts = parts.entries.take(maximum_shares).associate { it.toPair() }
                val partsList = partialParts.values.toList()
                connected.sort() // sort alphabetically for recovery


                if (!isAllRoles) {
                    for (i in connected.indices) {
                        communicationProtocol.connect(name, partsList[i]!!, connected[i])
                        myShares[i] = Pair(connected[i], ByteArray(0)) // myShares contains the ttp names for easier reconstruction
                    }
                }
                else { // in case of all roles
                    val allTTPs = ParticipantHolder.ttp?.plus(ParticipantHolder.regttp)
                    for (i in connected.indices) {
                        myShares[i] = Pair(
                            connected[i],
                            ByteArray(0)
                        ) // myShares contains the ttp names for easier reconstruction
                        val index = allTTPs?.indexOfFirst { it!!.name == connected[i] }
                        if (index != -1) {
                            allTTPs!![index!!]?.connectedUserManager?.addConnectedUser(name,partsList[i]!!)
                            allTTPs[index]?.connected_Users?.add((name to partsList[i]))
                        }
                    }
                }

            }

        }
        return
    }

    fun recoverShare(ttpName: String){
//        Log.i("adr_recover","asking to recover my share. my private is $privateKey\nmy public is $publicKey")
        val signature = Schnorr.schnorrSignature(privateKey, (name + ":" + System.currentTimeMillis().toString()).toByteArray(Charsets.UTF_8), group)
        if(connected.size < maximum_shares) {
            return
        }
        if (!isAllRoles) {

            val share = communicationProtocol.requestShare(signature,name,ttpName)
            var index = myShares.indexOfFirst { it.first == ttpName }
            if (index != -1) {
                myShares[index] = ttpName to share
            }
        }
        else{
            val signedMessage = signature.signedMessage.toString(Charsets.UTF_8)         // Extract signed message and verify timestamp and sender match
            val (signedUser, signedTimeStr) = signedMessage.split(":", limit = 2)
            val signedTime = signedTimeStr.toLongOrNull()
            val isValidTime = signedTime != null && abs(System.currentTimeMillis() - signedTime) <= 2 * 60 * 1000 // allows only 2 minutes for replay attack
            val communicationProtocol = communicationProtocol as IPV8CommunicationProtocol

            if (!(name == signedUser && isValidTime)) {
                // Log.i("adr", "Invalid signature timestamp or user mismatch. Time diff: ${System.currentTimeMillis() - (signedTime ?: 0)}")
                return
            }
            val allTTPs = ParticipantHolder.ttp?.plus(ParticipantHolder.regttp)

            var recipientIndex = allTTPs?.indexOfFirst { it!!.name == ttpName }

            val addressList = communicationProtocol.addressBookManager.getAllAddresses()

            val senderPK = addressList.find { it.name == name }?.publicKey

            if (senderPK == null) {
                return
            }
            val group = recipientIndex?.let { allTTPs?.get(it)?.group }!!

            val valid = Schnorr.verifySchnorrSignature(signature, senderPK, group)
            if (valid){
                var indexShare = myShares.indexOfFirst { it.first == ttpName }
                if (indexShare != -1) {
                    myShares[indexShare] = ttpName to allTTPs?.get(recipientIndex)!!.connected_Users.first{ it.first == name }.second
                }
                }

            }
        }


    fun withdrawDigitalEuro(bank: String): DigitalEuro {

        val serialNumber = UUID.randomUUID().toString()

        val firstT = group.getRandomZr()

        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()
        lateinit var bankRandomness: Element

        if (!isAllRoles) {
            bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        }
        else{
            val bankInstance = ParticipantHolder.bank
            val publicKey1 = bankInstance?.group?.gElementFromBytes(publicKey.toBytes())
            val randomness = bankInstance?.getBlindSignatureRandomness(publicKey1!!)
            if (randomness != null) {
                bankRandomness = group.gElementFromBytes(randomness.toBytes())
            }
        }


        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)

        lateinit var blindSignature: BigInteger

        if (!isAllRoles) {
            blindSignature = communicationProtocol.requestBlindSignature(publicKey, bank, blindedChallenge.blindedChallenge)
        } else {
            val bankInstance = ParticipantHolder.bank
            if (bankInstance == null) {
                throw NullPointerException("ParticipantHolder.bank is null")
            }

            val publicKeyLocal = bankInstance.group.gElementFromBytes(publicKey.toBytes())

            val sign = bankInstance.createBlindSignature(blindedChallenge.blindedChallenge, publicKeyLocal)
            blindSignature = BigInteger(sign.toByteArray())
        }

        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)

        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())

        wallet.addToWallet(digitalEuro, firstT)

        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")

        return digitalEuro }
    fun getBalance(): Int {
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        Log.i("adr author3.01","tranaction created")

        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        Log.i("adr author3.1","tranaction created")

        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)
        Log.i("adr author3.2","tranaction created")

        if (transactionResult.valid) {
            Log.i("adr coin received","adding to wallet!")

            wallet.addToWallet(transactionDetails, usedRandomness)
            //onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        //onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override suspend fun reset() {
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()

        setUp()
    }
}
