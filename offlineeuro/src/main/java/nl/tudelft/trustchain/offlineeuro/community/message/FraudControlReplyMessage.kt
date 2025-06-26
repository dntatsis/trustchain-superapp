package nl.tudelft.trustchain.offlineeuro.community.message

class FraudControlReplyMessage(
    val name: String,
    val result: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.FraudControlReplyMessage
}
