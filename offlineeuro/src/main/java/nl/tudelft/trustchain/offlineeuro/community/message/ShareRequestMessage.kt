package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class ShareRequestMessage(  // TODO: add proof
    val userName: String, // Whose shares?
    val peer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.ShareRequestMessage
}
