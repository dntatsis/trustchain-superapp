package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.enums.Role

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val role : Role,
    val peerPublicKeyBytes: ByteArray
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
