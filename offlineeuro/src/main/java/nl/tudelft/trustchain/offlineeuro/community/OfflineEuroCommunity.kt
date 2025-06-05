package nl.tudelft.trustchain.offlineeuro.community

import android.util.Log
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.ShareRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPConnectionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.AddressPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BilinearGroupCRSPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BlindSignatureRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.ByteArrayPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.FraudControlRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.ShareRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPRegistrationPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPConnectionPayload
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.message.ShareResponseMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.ShareResponsePayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TransactionDetailsPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TransactionRandomizationElementsPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.math.BigInteger

object MessageID {
    const val GET_GROUP_DESCRIPTION_CRS = 9
    const val GET_GROUP_DESCRIPTION_CRS_REPLY = 10
    const val REGISTER_AT_TTP = 11

    const val GET_BLIND_SIGNATURE_RANDOMNESS = 12
    const val GET_BLIND_SIGNATURE_RANDOMNESS_REPLY = 13
    const val GET_BLIND_SIGNATURE = 14
    const val GET_BLIND_SIGNATURE_REPLY = 15

    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS = 16
    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY = 17

    const val TRANSACTION = 18
    const val TRANSACTION_RESULT = 19
    const val SCOPE_PEERS = 20
    const val ADDRESS_RESPONSE = 21

    const val FRAUD_CONTROL_REQUEST = 22
    const val FRAUD_CONTROL_REPLY = 23

    const val CONNECT_TO_TTP = 24

    const val REQUEST_SHARE = 25

    const val REQUEST_SHARE_RESPONSE = 26
}

class OfflineEuroCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    lateinit var messageList: MessageList<ICommunityMessage>

    init {

        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS] = ::onGetGroupDescriptionAndCRSPacket
        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY] = ::onGetGroupDescriptionAndCRSReplyPacket

        messageHandlers[MessageID.REGISTER_AT_TTP] = ::onGetRegisterAtTTPPacket

        messageHandlers[MessageID.CONNECT_TO_TTP] = ::onGetConnectAtTTPPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS] = ::onGetBlindSignatureRandomnessPacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY] = ::onGetBlindSignatureRandomnessReplyPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE] = ::onGetBlindSignaturePacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_REPLY] = ::onGetBlindSignatureReplyPacket

        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS] = ::onGetTransactionRandomizationElementsRequestPacket
        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY] = ::onGetTransactionRandomizationElementsReplyPacket

        messageHandlers[MessageID.TRANSACTION] = ::onTransactionPacket
        messageHandlers[MessageID.TRANSACTION_RESULT] = ::onTransactionResultPacket

        messageHandlers[MessageID.SCOPE_PEERS] = ::onScopePeersPacket
        messageHandlers[MessageID.ADDRESS_RESPONSE] = ::onAddressReplyPacket

        messageHandlers[MessageID.FRAUD_CONTROL_REQUEST] = ::onFraudControlRequestPacket
        messageHandlers[MessageID.FRAUD_CONTROL_REPLY] = ::onFraudControlReplyPacket

        messageHandlers[MessageID.REQUEST_SHARE] = ::onShareRequestPacket
        messageHandlers[MessageID.REQUEST_SHARE_RESPONSE] = ::onShareResponsePacket

    }

    fun getGroupDescriptionAndCRS() {

        val packet =
            serializePacket(
                MessageID.GET_GROUP_DESCRIPTION_CRS,
                ByteArrayPayload(myPeer.publicKey.keyToBin())
            )

        for (peer: Peer in getPeers()) {
            send(peer, packet)
        }
    }

    private fun onGetGroupDescriptionAndCRSPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetGroupDescriptionAndCRS(requestingPeer)
    }

    fun onGetGroupDescriptionAndCRS(requestingPeer: Peer) {
        val message = BilinearGroupCRSRequestMessage(requestingPeer)
        messageList.add(message)
    }

    fun sendGroupDescriptionAndCRS(
        groupBytes: BilinearGroupElementsBytes,
        crsBytes: CRSBytes,
        ttpPublicKeyBytes: ByteArray,
        requestingPeer: Peer
    ) {
        val groupAndCrsPacket =
            serializePacket(
                MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY,
                BilinearGroupCRSPayload(groupBytes, crsBytes, ttpPublicKeyBytes)
            )

        send(requestingPeer, groupAndCrsPacket)
    }

    private fun onGetGroupDescriptionAndCRSReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(BilinearGroupCRSPayload)
        onGetGroupDescriptionAndCRSReply(payload, peer)
    }

    fun onGetGroupDescriptionAndCRSReply(
        payload: BilinearGroupCRSPayload,
        peer: Peer
    ) {
        val groupElements = payload.bilinearGroupElements
        val crs = payload.crs

        val ttpAddressMessage = AddressMessage("TTP", Role.REG_TTP, payload.ttpPublicKey, peer.publicKey.keyToBin()) // TODO: Get rid of hardcoded "TTP"s!

        val message = BilinearGroupCRSReplyMessage(groupElements, crs, ttpAddressMessage)
        addMessage(message)
    }

    fun registerAtTTP(
        name: String,
        myPublicKeyBytes: ByteArray,
        role: Role,
        publicKeyTTP: ByteArray
    ) {
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")

        val registerPacket =
            serializePacket(
                MessageID.REGISTER_AT_TTP,
                TTPRegistrationPayload(
                    name,
                    myPublicKeyBytes,
                    role
                )
            )

        send(ttpPeer, registerPacket)
    }

    fun requestSharefromTTP(
        signature: SchnorrSignature,
        name : String,
        publicKeyTTP: ByteArray
    ){
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")

        val registerPacket =
            serializePacket(
                MessageID.REQUEST_SHARE,
                ShareRequestPayload(
                    signature,
                    name
                )
            )

        send(ttpPeer, registerPacket)
    }

    fun onGetConnectAtTTPPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(TTPConnectionPayload)
        onGetConnectAtTTP(peer, payload)
    }

    fun onGetConnectAtTTP(
        peer: Peer,
        payload: TTPConnectionPayload
    ) {
        val userName = payload.userName
        val secretShare = payload.secretShare

        val message =
            TTPConnectionMessage(
                userName,
                secretShare
            )
        addMessage(message)
    }
    fun onGetRegisterAtTTPPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(TTPRegistrationPayload)
        onGetRegisterAtTTP(peer, payload)
    }
    fun onShareRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ShareRequestPayload)
        onGetShareRequestAtTTP(peer, payload)
    }
    fun onShareResponsePacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ShareResponsePayload)
        onGetShareResponseAtTTP(peer, payload)
    }
    fun connectAtTTP(
        name: String,
        secretShare: ByteArray,
        publicKeyTTP: ByteArray
    ) {
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")

        val registerPacket =
            serializePacket(
                MessageID.CONNECT_TO_TTP,
                TTPConnectionPayload(
                    name,
                    secretShare
                )
            )

        send(ttpPeer, registerPacket)
    }

    fun onGetRegisterAtTTP(
        peer: Peer,
        payload: TTPRegistrationPayload
    ) {
        val senderPKBytes = peer.publicKey.keyToBin()
        val userName = payload.userName
        val userPKBytes = payload.publicKey
        val userRole = payload.role

        val message =
            TTPRegistrationMessage(
                userName,
                userPKBytes,
                userRole,
                senderPKBytes
            )

        addMessage(message)
    }

    fun sendShareRequestResponsePacket(
        peer: Peer,
        payload: ShareResponsePayload

    ){
        val registerPacket =
            serializePacket(
                MessageID.REQUEST_SHARE_RESPONSE,
                payload
            )

        send(peer, registerPacket)
    }
    fun onGetShareRequestAtTTP(
        peer: Peer,
        payload: ShareRequestPayload
    )  {
        val message = ShareRequestMessage(payload.signature, payload.name, peer)
        addMessage(message)

    }
    fun onGetShareResponseAtTTP(peer: Peer,
                                payload: ShareResponsePayload
    )  {
        val message = ShareResponseMessage(payload.userName,payload.secretShare,payload.sender)
        addMessage(message)

    }
    fun getBlindSignatureRandomness(
        userPublicKeyBytes: ByteArray,
        publicKeyBank: ByteArray
    ) {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank)

        bankPeer ?: throw Exception("Bank not found")

        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE_RANDOMNESS,
                ByteArrayPayload(
                    userPublicKeyBytes
                )
            )

        send(bankPeer, packet)
    }

    fun onGetBlindSignatureRandomnessPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomness(requestingPeer, payload)
    }

    fun onGetBlindSignatureRandomness(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val publicKey = payload.bytes
        val message =
            BlindSignatureRandomnessRequestMessage(
                publicKey,
                requestingPeer
            )
        addMessage(message)
    }

    fun sendBlindSignatureRandomnessReply(
        randomnessBytes: ByteArray,
        peer: Peer
    ) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY, ByteArrayPayload(randomnessBytes))
        send(peer, packet)
    }

    fun onGetBlindSignatureRandomnessReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomnessReply(payload)
    }

    fun onGetBlindSignatureRandomnessReply(payload: ByteArrayPayload) {
        val message = BlindSignatureRandomnessReplyMessage(payload.bytes)
        addMessage(message)
    }

    fun getBlindSignature(
        challenge: BigInteger,
        publicKeyBytes: ByteArray,
        bankPublicKeyBytes: ByteArray
    ) {
        val bankPeer = getPeerByPublicKeyBytes(bankPublicKeyBytes)

        bankPeer ?: throw Exception("Bank not found")
        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE,
                BlindSignatureRequestPayload(
                    challenge,
                    publicKeyBytes
                )
            )

        send(bankPeer, packet)
    }

    fun onGetBlindSignaturePacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(BlindSignatureRequestPayload)
        onGetBlindSignature(requestingPeer, payload)
    }

    fun onGetBlindSignature(
        requestingPeer: Peer,
        payload: BlindSignatureRequestPayload
    ) {
        val message =
            BlindSignatureRequestMessage(
                payload.challenge,
                payload.publicKeyBytes,
                requestingPeer
            )
        addMessage(message)
    }

    fun sendBlindSignature(
        signature: BigInteger,
        peer: Peer
    ) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_REPLY, ByteArrayPayload(signature.toByteArray()))
        send(peer, packet)
    }

    fun onGetBlindSignatureReplyPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureReply(requestingPeer, payload)
    }

    fun onGetBlindSignatureReply(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val message =
            BlindSignatureReplyMessage(
                BigInteger(payload.bytes)
            )
        addMessage(message)
    }

    fun getTransactionRandomizationElements(
        publicKey: ByteArray,
        publicKeyReceiver: ByteArray
    ) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet =
            serializePacket(
                MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS,
                ByteArrayPayload(
                    publicKey
                )
            )

        send(peer, packet)
    }

    fun onGetTransactionRandomizationElementsRequestPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetTransactionRandomizationElementsRequest(requestingPeer, payload)
    }

    fun onGetTransactionRandomizationElementsRequest(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val message = TransactionRandomizationElementsRequestMessage(payload.bytes, requestingPeer)
        addMessage(message)
    }

    fun sendTransactionRandomizationElements(
        randomizationElementsBytes: RandomizationElementsBytes,
        requestingPeer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY,
                TransactionRandomizationElementsPayload(randomizationElementsBytes)
            )
        send(requestingPeer, packet)
    }

    fun onGetTransactionRandomizationElementsReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(TransactionRandomizationElementsPayload)
        onGetTransactionRandomizationElements(payload)
    }

    fun onGetTransactionRandomizationElements(payload: TransactionRandomizationElementsPayload) {
        val message = TransactionRandomizationElementsReplyMessage(payload.transactionRandomizationElementsBytes)
        addMessage(message)
    }

    fun sendTransactionDetails(
        publicKeyBytes: ByteArray,
        publicKeyReceiver: ByteArray,
        transactionDetails: TransactionDetailsBytes
    ) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet =
            serializePacket(
                MessageID.TRANSACTION,
                TransactionDetailsPayload(
                    publicKeyBytes,
                    transactionDetails
                )
            )

        send(peer, packet)
    }

    fun onTransactionPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(TransactionDetailsPayload)
        onTransaction(peer, payload)
    }

    fun onTransaction(
        peer: Peer,
        payload: TransactionDetailsPayload
    ) {
        val publicKey = payload.publicKey
        val transactionDetailsBytes = payload.transactionDetailsBytes
        val message = TransactionMessage(publicKey, transactionDetailsBytes, peer)
        messageList.add(message)
    }

    fun sendTransactionResult(
        transactionResult: String,
        requestingPeer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.TRANSACTION_RESULT,
                ByteArrayPayload(transactionResult.toByteArray())
            )
        send(requestingPeer, packet)
    }

    fun onTransactionResultPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ByteArrayPayload)
        onTransactionResult(payload)
    }

    fun onTransactionResult(byteArrayPayload: ByteArrayPayload) {
        val message = TransactionResultMessage(byteArrayPayload.bytes.toString(Charsets.UTF_8))
        messageList.add(message)
    }

    fun sendAddressReply(
        name: String,
        role: Role,
        publicKeyBytes: ByteArray,
        requestingPeer: Peer
    ) {
        val addressPacket =
            serializePacket(
                MessageID.ADDRESS_RESPONSE,
                AddressPayload(
                    name,
                    publicKeyBytes,
                    role
                )
            )

        send(requestingPeer, addressPacket)
    }

    private fun onAddressReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(AddressPayload)
        onAddressReply(payload, peer)
    }

    private fun onAddressReply(
        payload: AddressPayload,
        peer: Peer
    ) {
        val message = AddressMessage(payload.userName, payload.role, payload.publicKey, peer.publicKey.keyToBin())
        addMessage(message)
    }

    fun sendFraudControlRequest(
        firstProofBytes: ByteArray,
        secondProofBytes: ByteArray,
        ttpPublicKeyBytes: ByteArray
    ) {
        val peer = getPeerByPublicKeyBytes(ttpPublicKeyBytes)

        peer ?: throw Exception("TTP not found")

        val packet =
            serializePacket(
                MessageID.FRAUD_CONTROL_REQUEST,
                FraudControlRequestPayload(
                    firstProofBytes,
                    secondProofBytes
                )
            )

        send(peer, packet)
    }

    fun onFraudControlRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(FraudControlRequestPayload)
        onFraudControlRequest(peer, payload)
    }

    private fun onFraudControlRequest(
        peer: Peer,
        payload: FraudControlRequestPayload
    ) {
        val message = FraudControlRequestMessage(payload.firstProofBytes, payload.secondProofBytes, peer)
        addMessage(message)
    }

    fun sendFraudControlReply(
        result: String,
        peer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.FRAUD_CONTROL_REPLY,
                ByteArrayPayload(
                    result.toByteArray()
                )
            )
        send(peer, packet)
    }

    fun onFraudControlReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ByteArrayPayload)
        onFraudControlReply(payload)
    }

    fun onFraudControlReply(payload: ByteArrayPayload) {
        val fraudControlResult = payload.bytes.toString(Charsets.UTF_8)
        addMessage(FraudControlReplyMessage(fraudControlResult))
    }

    fun scopePeers(
        name: String,
        role: Role,
        publicKeyBytes: ByteArray
    ) {
        val addressPacket =
            serializePacket(
                MessageID.SCOPE_PEERS,
                AddressPayload(
                    name,
                    publicKeyBytes,
                    role
                )
            )
        // Log.i("adr_peers",getPeers().toString())
        for (peer in getPeers()) {
            send(peer, addressPacket)
        }
    }

    private fun onScopePeersPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(AddressPayload)
        onScopePeers(payload, requestingPeer)
    }

    private fun onScopePeers(
        payload: AddressPayload,
        requestingPeer: Peer
    ) {
        val addressMessage = AddressMessage(payload.userName, payload.role, payload.publicKey, requestingPeer.publicKey.keyToBin())
        addMessage(addressMessage)
        val addressRequestMessage = AddressRequestMessage(requestingPeer)

        addMessage(addressRequestMessage)
    }

    private fun getPeerByPublicKeyBytes(publicKey: ByteArray): Peer? {
        return getPeers().find {
            it.publicKey.keyToBin().contentEquals(publicKey)
        }
    }

    private fun getRandomPeer(publicKeyBank: ByteArray): Peer? {
        return getPeers().shuffled().find {
            !it.publicKey.keyToBin().contentEquals(publicKeyBank)
        }
    }

    fun addMessage(message: ICommunityMessage) {
        if (this::messageList.isInitialized) {
            messageList.add(message)
        }
    }

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<OfflineEuroCommunity>(OfflineEuroCommunity::class.java) {
        override fun create(): OfflineEuroCommunity {
            return OfflineEuroCommunity(settings, database, crawler)
        }
    }
}
