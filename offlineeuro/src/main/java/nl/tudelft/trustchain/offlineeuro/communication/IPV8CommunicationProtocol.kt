package nl.tudelft.trustchain.offlineeuro.communication

import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.ShareRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ShareResponseMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPConnectionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.ShareResponsePayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP

import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import java.math.BigInteger
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import kotlin.math.abs


class IPV8CommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity,
) : ICommunicationProtocol {
    val messageList = MessageList(this::handleRequestMessage)

    init {
        community.messageList = messageList
    }

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10000
    override lateinit var participant: Participant

    override suspend fun getGroupDescriptionAndCRS() {
        community.getGroupDescriptionAndCRS()
        val message =
            waitForMessageAsync(CommunityMessageType.GroupDescriptionCRSReplyMessage) as BilinearGroupCRSReplyMessage
        Log.i("adr","message received!\ncontents are as follows:\ng:${message.groupDescription.g}\nh:${message.groupDescription.h}\ngt:${message.groupDescription.gt}")
        if(participant is TTP){ // copy regttp group into ttp
            (participant as TTP).regGroup.updateGroupElements(message.groupDescription)
            val crs = message.crs.toCRS((participant as TTP).regGroup)
            (participant as TTP).regCrs = crs
            Log.d("adr_GROUPCHECK","g:${(participant as TTP).regGroup.g}, \nh:${(participant as TTP).regGroup.h}, \ngt:${(participant as TTP).regGroup.gt}")

        }
        else{
            participant.group.updateGroupElements(message.groupDescription)
            val crs = message.crs.toCRS(participant.group)
            participant.crs = crs
            Log.d("adr_GROUPCHECK","g:${participant.group.g}, \nh:${participant.group.h},\ngt:${participant.group.gt}")

            messageList.add(message.addressMessage)

        }
    }

    override fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String
    ) {

        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.registerAtTTP(userName, publicKey.toBytes(), getParticipantRole(), ttpAddress.peerPublicKey!!)
    }

    override fun requestShare( // send your request for a share
        signature: SchnorrSignature,
        name : String,
        ttpname: String
    ){
        val ttpAddress = addressBookManager.getAddressByName(ttpname)

        community.requestSharefromTTP(signature, name, ttpAddress.peerPublicKey!!)
    }
    override fun connect( // send your share to a connected TTP
        userName: String,
        secretShare: ByteArray,
        nameTTP: String
    ) {

        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.connectAtTTP(userName, secretShare, ttpAddress.peerPublicKey!!)
    }

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage =
            waitForMessage(CommunityMessageType.BlindSignatureRandomnessReplyMessage) as BlindSignatureRandomnessReplyMessage
        return group.gElementFromBytes(replyMessage.randomnessBytes)
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage = waitForMessage(CommunityMessageType.BlindSignatureReplyMessage) as BlindSignatureReplyMessage
        return replyMessage.signature
    }

    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.getTransactionRandomizationElements(participant.publicKey.toBytes(), peerAddress.peerPublicKey!!)
        val message = waitForMessage(CommunityMessageType.TransactionRandomnessReplyMessage) as TransactionRandomizationElementsReplyMessage
        return message.randomizationElementsBytes.toRandomizationElements(group)
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.sendTransactionDetails(
            participant.publicKey.toBytes(),
            peerAddress.peerPublicKey!!,
            transactionDetails.toTransactionDetailsBytes()
        )
        val message = waitForMessage(CommunityMessageType.TransactionResultMessage) as TransactionResultMessage
        return message.result
    }

    override fun requestFraudControl(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof,
        nameTTP: String
    ): String {
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.sendFraudControlRequest(
            GrothSahaiSerializer.serializeGrothSahaiProof(firstProof),
            GrothSahaiSerializer.serializeGrothSahaiProof(secondProof),
            ttpAddress.peerPublicKey!!
        )
        val message = waitForMessage(CommunityMessageType.FraudControlReplyMessage) as FraudControlReplyMessage
        return message.result
    }

    fun scopePeers() {
        community.scopePeers(participant.name, getParticipantRole(), participant.publicKey.toBytes())
    }

    override fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element {
        return addressBookManager.getAddressByName(name).publicKey
    }

    private suspend fun waitForMessageAsync(messageType: CommunityMessageType): ICommunityMessage {
        var loops = 0

        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * sleepDuration >= timeOutInMS) {
                throw Exception("TimeOut")
            }
            delay(sleepDuration)
            loops++
        }

        val message =
            community.messageList.first { it.messageType == messageType }
        community.messageList.remove(message)

        return message
    }

    private fun waitForMessage(messageType: CommunityMessageType): ICommunityMessage {
        var loops = 0

        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * sleepDuration >= timeOutInMS) {
                throw Exception("TimeOut")
            }
            Thread.sleep(sleepDuration)
            loops++
        }

        val message =
            community.messageList.first { it.messageType == messageType }
        community.messageList.remove(message)

        return message
    }

    private fun handleAddressMessage(message: AddressMessage) {
        val publicKey = participant.group.gElementFromBytes(message.publicKeyBytes)
        val address = Address(message.name, message.role, publicKey, message.peerPublicKey)
        addressBookManager.insertAddress(address)
        participant.onDataChangeCallback?.invoke("addr_mess_recv by $message.name") // participant has received an address
    }

    private fun handleGetBilinearGroupAndCRSRequest(message: BilinearGroupCRSRequestMessage) {
        if (participant !is REGTTP) { // only registrar should respond to such messages
            return
        } else {
            val groupBytes = participant.group.toGroupElementBytes()
            val crsBytes = participant.crs.toCRSBytes()
            val peer = message.requestingPeer
            Log.i("adr","received a Group request")
            community.sendGroupDescriptionAndCRS(
                groupBytes,
                crsBytes,
                participant.publicKey.toBytes(),
                peer
            )
        }
    }

    private fun handleBlindSignatureRandomnessRequest(message: BlindSignatureRandomnessRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)
        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val randomness = bank.getBlindSignatureRandomness(publicKey)
        val requestingPeer = message.peer
        community.sendBlindSignatureRandomnessReply(randomness.toBytes(), requestingPeer)
    }

    private fun handleBlindSignatureRequestMessage(message: BlindSignatureRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)

        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val challenge = message.challenge
        val signature = bank.createBlindSignature(challenge, publicKey)
        val requestingPeer = message.peer
        community.sendBlindSignature(signature, requestingPeer)
    }

    private fun handleTransactionRandomizationElementsRequest(message: TransactionRandomizationElementsRequestMessage) {
        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKey)
        val requestingPeer = message.requestingPeer

        val randomizationElements = participant.generateRandomizationElements(publicKey)
        val randomizationElementBytes = randomizationElements.toRandomizationElementsBytes()
        community.sendTransactionRandomizationElements(randomizationElementBytes, requestingPeer)
    }

    private fun handleTransactionMessage(message: TransactionMessage) {
        val bankPublicKey =
            if (participant is Bank) {
                participant.publicKey
            } else {
                addressBookManager.getAddressByName("Bank").publicKey
            }

        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKeyBytes)
        val transactionDetailsBytes = message.transactionDetailsBytes
        val transactionDetails = transactionDetailsBytes.toTransactionDetails(group)
        val transactionResult = participant.onReceivedTransaction(transactionDetails, bankPublicKey, publicKey)
        val requestingPeer = message.requestingPeer
        community.sendTransactionResult(transactionResult, requestingPeer)
    }

    private fun handleRegistrationMessage(message: TTPRegistrationMessage) {
        if (participant !is REGTTP) {
            return
        }

        val ttp = participant as REGTTP
        val publicKey = ttp.group.gElementFromBytes(message.userPKBytes)
        if (message.role == Role.User){
            Log.i("adr","received user registration in TTP")
            ttp.registerUser(message.userName, publicKey)
        }
        else if(message.role == Role.Bank){
            Log.i("adr","received bank registration in TTP")
            ttp.registerUser(message.userName, publicKey)
        }
    }
    private fun handleShareRequestMessage(message: ShareRequestMessage) {
        // Only process if participant is REGTTP or TTP
        if (participant !is REGTTP && participant !is TTP) return

        val ttp = participant as TTP
        val sender = message.userName
        val addressList = addressBookManager.getAllAddresses()

        val signedMessage = message.signature.signedMessage.toString(Charsets.UTF_8)         // Extract signed message and verify timestamp and sender match
        val (signedUser, signedTimeStr) = signedMessage.split(":", limit = 2)
        val signedTime = signedTimeStr.toLongOrNull()
        val isValidTime = signedTime != null && abs(System.currentTimeMillis() - signedTime) <= 2 * 60 * 1000 // allows only 2 minutes for replay attack

        if (sender == signedUser && isValidTime) {
            Log.i("adr", "$signedMessage seems fine (not expired, matching sender)")
        } else {
            Log.i("adr", "Invalid signature timestamp or user mismatch. Time diff: ${System.currentTimeMillis() - (signedTime ?: 0)}")
            return
        }

        Log.i("adr", "Searching for $sender in \n$addressList")
        val senderPK = addressList.find { it.name == sender }?.publicKey

        if (senderPK == null) {
            Log.i("adr", "User $sender not found in address book.")
            return
        }

        val group = if (participant is REGTTP) ttp.group else ttp.regGroup

        Log.i("adr", "g = ${group.g}")
        Log.i("adr", "group order = ${group.getZrOrder()}")

        val valid = Schnorr.verifySchnorrSignature(message.signature, senderPK, group)

        Log.i("adrspecial", "Valid: $valid, from: $sender, \nmessage: $signedMessage\n${message.signature.signedMessage.contentEquals(
            "$signedUser:$signedTimeStr".toByteArray(Charsets.UTF_8)
        )}, from PK: $senderPK")

        if (!valid) {
            Log.i("adrspecial", "Signature verification failed - possible impersonation!")
            return
        }

        val share = ttp.getSharefromTTP(sender)
        if (share != null) {
            val response = ShareResponsePayload(sender, share, ttp.name)
            community.sendShareRequestResponsePacket(message.peer, response)
        }
    }
    private fun handleShareResponseMessage(message: ShareResponseMessage){ // When receiving a Share Response, trigger the callback
        if(participant is User && message.userName == participant.name){
            // partial secret share has been returned.
                val index = (participant as User).myShares.indexOfFirst { it.first == message.sender }

                if (index != -1) {
                    (participant as User).myShares[index] = message.sender to message.secretShare
                } else {
                    Log.i("adr recover","Name not found in the list")
                }
            participant.onDataChangeCallback?.invoke("secret_share_recv " + message.secretShare.toString())
        }
        // TODO: add bank logic here
        return

    }
    private fun handleConnectionMessage(message: TTPConnectionMessage) { // Handle TTP Connection message by adding the share to the participants secret share library.
        if (participant !is REGTTP && participant !is TTP) {
            return
        }

        val ttp = participant as TTP
        ttp.connectUser(message.userName, message.secretShare)
    }
    private fun handleAddressRequestMessage(message: AddressRequestMessage) {
        val role = getParticipantRole()
        community.sendAddressReply(participant.name, role, participant.publicKey.toBytes(), message.requestingPeer)
    }

    private fun handleFraudControlRequestMessage(message: FraudControlRequestMessage) {
        if (getParticipantRole() != Role.REG_TTP) {
            return
        }
        val ttp = participant as TTP
        val firstProof = GrothSahaiSerializer.deserializeProofBytes(message.firstProofBytes, participant.group)
        val secondProof = GrothSahaiSerializer.deserializeProofBytes(message.secondProofBytes, participant.group)
        val result = ttp.getUserFromProofs(firstProof, secondProof)
        community.sendFraudControlReply(result, message.requestingPeer)
    }

    private fun handleRequestMessage(message: ICommunityMessage) {
        when (message) {
            is AddressMessage -> handleAddressMessage(message)
            is ShareRequestMessage -> handleShareRequestMessage(message)
            is ShareResponseMessage -> handleShareResponseMessage(message)
            is TTPConnectionMessage -> handleConnectionMessage(message)
            is AddressRequestMessage -> handleAddressRequestMessage(message)
            is BilinearGroupCRSRequestMessage -> handleGetBilinearGroupAndCRSRequest(message)
            is BlindSignatureRandomnessRequestMessage -> handleBlindSignatureRandomnessRequest(message)
            is BlindSignatureRequestMessage -> handleBlindSignatureRequestMessage(message)
            is TransactionRandomizationElementsRequestMessage -> handleTransactionRandomizationElementsRequest(message)
            is TransactionMessage -> handleTransactionMessage(message)
            is TTPRegistrationMessage -> handleRegistrationMessage(message)
            is FraudControlRequestMessage -> handleFraudControlRequestMessage(message)
            else -> throw Exception("Unsupported message type")
        }
        return
    }

    private fun getParticipantRole(): Role {

        return when (participant) {
            is User -> Role.User
            is REGTTP -> Role.REG_TTP
            is TTP -> Role.TTP
            is Bank -> Role.Bank
            else -> throw Exception("Unknown role")
        }
    }
}
