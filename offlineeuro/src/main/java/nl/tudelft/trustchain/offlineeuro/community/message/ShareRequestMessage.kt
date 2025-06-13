package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature

class ShareRequestMessage(  // sent by user to recover their shares
    val signature: SchnorrSignature, // user signature of message "userName:timestamp"
    val userName: String,
    val peer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.ShareRequestMessage
}
