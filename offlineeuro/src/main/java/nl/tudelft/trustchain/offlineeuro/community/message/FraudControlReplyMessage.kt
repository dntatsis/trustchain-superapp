package nl.tudelft.trustchain.offlineeuro.community.message

class FraudControlReplyMessage(
    val result: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.FraudControlReplyMessage
}
