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
    companion object {
        const val maximum_shares = 3
        const val minimum_shares = 2
    }
    lateinit var scheme: Scheme
    lateinit var wallet: Wallet
    val myShares: MutableList<Pair<String,ByteArray>> =  MutableList(maximum_shares) { Pair("", ByteArray(0)) }
        init {
        communicationProtocol.participant = this
        this.group = group

        if (runSetup) generateKeyPair()

        if (walletManager == null) {
            walletManager = WalletManager(context, group,"wallet_$name")
            Log.i("Adr wallet manager", walletManager.toString())
        }
    }

     suspend fun setup() {
         setUp()
         // Need private key from setup to generate a new wallet
         wallet = Wallet(privateKey, publicKey, walletManager!!)
     }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        // TODO NEW: alternative for offline
        lateinit var randomizationElements: RandomizationElements
        if (!isAllRoles){
            randomizationElements =  communicationProtocol.requestTransactionRandomness(nameReceiver, group) // message exchange 1
        }
        else{
            Log.i("adr_recover","asking to send a euro")

            var sent_pk = publicKey.toBytes()

            var combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
            var index = combinedUserAndBank!!.indexOfFirst {it!!.name == nameReceiver}

            if (index != -1){
                val publicKeyDec = combinedUserAndBank[index]!!.group.gElementFromBytes(sent_pk)
                Log.i("adr send","$index: $combinedUserAndBank \n $publicKey,\n $publicKeyDec")

                val randomizationElementsOther = combinedUserAndBank[index]!!.generateRandomizationElements(publicKeyDec)
                val randomizationElementBytes = randomizationElementsOther.toRandomizationElementsBytes()
                randomizationElements = randomizationElementBytes.toRandomizationElements(group)
                Log.i("adr send sign check","$randomizationElementsOther\n$randomizationElements,\n${randomizationElements == randomizationElementsOther}")
            }

        }
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        lateinit var result:String
        if (!isAllRoles) {
            result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails) // message exchange 2
        }
        else{
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

            }


        }
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        // TODO NEW: alternative for offline

        lateinit var randomizationElements: RandomizationElements
        if (!isAllRoles){
            randomizationElements =  communicationProtocol.requestTransactionRandomness(nameReceiver, group) // message exchange 1
        }
        else{
            Log.i("adr_recover","asking to send a euro")

            var sent_pk = publicKey.toBytes()

            var combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
            var index = combinedUserAndBank!!.indexOfFirst {it!!.name == nameReceiver}

            if (index != -1){
                val publicKeyDec = combinedUserAndBank[index]!!.group.gElementFromBytes(sent_pk)
                Log.i("adr send","$index: $combinedUserAndBank \n $publicKey,\n $publicKeyDec")

                val randomizationElementsOther = combinedUserAndBank[index]!!.generateRandomizationElements(publicKeyDec)
                val randomizationElementBytes = randomizationElementsOther.toRandomizationElementsBytes()
                randomizationElements = randomizationElementBytes.toRandomizationElements(group)
                Log.i("adr send sign check","$randomizationElementsOther\n$randomizationElements,\n${randomizationElements == randomizationElementsOther}")
            }

        }
        Log.i("adr wallet","about to double spend")


        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs) ?: throw Exception("No euro to send")
        lateinit var result:String
        if (!isAllRoles) {
            result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails) // message exchange 2
        }
        else{
            var transactionDetailsToB = transactionDetails.toTransactionDetailsBytes()
            var sentPK = publicKey.toBytes()
            var combinedUserAndBank = ParticipantHolder.user?.plus(ParticipantHolder.bank)
            var index = combinedUserAndBank!!.indexOfFirst {it!!.name == nameReceiver}
            Log.i("adr send","$index: $combinedUserAndBank")
            if (index != -1) {

                val groupRecv = combinedUserAndBank[index]!!.group

                val publicKeyRecv = groupRecv.gElementFromBytes(sentPK)

                val transactionDetailsRecv = transactionDetailsToB.toTransactionDetails(groupRecv)

                val transactionResult = combinedUserAndBank[index]!!.onReceivedTransaction(transactionDetailsRecv, ParticipantHolder.bank!!.publicKey, publicKeyRecv)

                result = transactionResult

            }


        }
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
                    // TODO: add message to the reception side
                        val index = allTTPs?.indexOfFirst { it!!.name == connected[i] }
                        if (index != -1) {
                            allTTPs!![index!!]?.connectedUserManager?.addConnectedUser(name,partsList[i]!!)
                            allTTPs[index]?.connected_Users?.add((name to partsList[i]))
                            Log.i("adr added","added $index (${connected[i]})")
                        }
                    }
                }

            }

        }
        return
    }

    fun recoverShare(ttpName: String){
        Log.i("adr_recover","asking to recover my share. my private is $privateKey\nmy public is $publicKey")
        val signature = Schnorr.schnorrSignature(privateKey, (name + ":" + System.currentTimeMillis().toString()).toByteArray(Charsets.UTF_8), group)

        if (!isAllRoles) {

        communicationProtocol.requestShare(signature,name,ttpName)
        communicationProtocol.requestShare(Schnorr.schnorrSignature(privateKey, ("Alice:" + System.currentTimeMillis().toString()).toByteArray(Charsets.UTF_8), group),"Alice",ttpName) // fails - unless you're alice
        }
        else{
            val signedMessage = signature.signedMessage.toString(Charsets.UTF_8)         // Extract signed message and verify timestamp and sender match
            val (signedUser, signedTimeStr) = signedMessage.split(":", limit = 2)
            val signedTime = signedTimeStr.toLongOrNull()
            val isValidTime = signedTime != null && abs(System.currentTimeMillis() - signedTime) <= 2 * 60 * 1000 // allows only 2 minutes for replay attack
            val communicationProtocol = communicationProtocol as IPV8CommunicationProtocol

            if (name == signedUser && isValidTime) {
                Log.i("adr", "$signedMessage seems fine (not expired, matching sender)")
            } else {
                Log.i("adr", "Invalid signature timestamp or user mismatch. Time diff: ${System.currentTimeMillis() - (signedTime ?: 0)}")
                return
            }
            val allTTPs = ParticipantHolder.ttp?.plus(ParticipantHolder.regttp)

            var index = allTTPs?.indexOfFirst { it!!.name == ttpName }

            val addressList = communicationProtocol.addressBookManager.getAllAddresses()

            Log.i("adr", "Searching for $name in \n$addressList")
            val senderPK = addressList.find { it.name == name }?.publicKey

            if (senderPK == null) {
                Log.i("adr", "User $name not found in address book.")
                return
            }
            val group = index?.let { allTTPs?.get(it)?.group }!!

            val valid = Schnorr.verifySchnorrSignature(signature, senderPK, group)
            if (valid){
                var index2 = myShares.indexOfFirst { it.first == ttpName }
                if (index2 != -1) {
                    myShares[index2] = ttpName to allTTPs?.get(index)!!.connected_Users.first{ it.first == name }.second
                }
                } 

            }
            else{
                Log.d("adr","Bad signature attempt!")
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
        Log.i("Withdraw", "Created DEuro, ${wallet.getAllWalletEntriesToSpend()}")

        wallet.addToWallet(digitalEuro, firstT)
        Log.i("Withdraw", "added DEuro, ${wallet.getAllWalletEntriesToSpend()}")

        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")
        Log.i("Withdraw", "Withdrawal complete")

        return digitalEuro }
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
