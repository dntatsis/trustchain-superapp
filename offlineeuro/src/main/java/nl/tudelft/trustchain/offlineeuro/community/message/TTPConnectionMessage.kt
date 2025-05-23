package nl.tudelft.trustchain.offlineeuro.community.message

class TTPConnectionMessage(
    val userName: String,
    val secretShare: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPConnectionMessage
}
