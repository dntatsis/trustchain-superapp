package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class ShareResponseMessage (
    val userName: String, // whose share was requested
    val secretShare: ByteArray,
    val sender: String // who had the share
) : ICommunityMessage {
    override val messageType = CommunityMessageType.ShareResponseMessage
}
