package nl.tudelft.trustchain.offlineeuro.community.message

class MessageList<ICommunityMessage>(private val onRequestMessageAdded: (ICommunityMessage) -> Unit) : ArrayList<ICommunityMessage>() {
    private val requestMessageTypes =
        setOf(
            AddressMessage::class.java,
            AddressRequestMessage::class.java,
            BilinearGroupCRSRequestMessage::class.java,
            BlindSignatureRandomnessRequestMessage::class.java,
            BlindSignatureRequestMessage::class.java,
            TransactionRandomizationElementsRequestMessage::class.java,
            TransactionMessage::class.java,
            TTPRegistrationMessage::class.java,
            FraudControlRequestMessage::class.java,
            TTPConnectionMessage::class.java
        )

    override fun add(element: ICommunityMessage): Boolean {
        return if (isRequestMessageType(element)) {
            onRequestMessageAdded(element)
            true
        } else {
            super.add(element)
        }
    }

    private fun isRequestMessageType(element: ICommunityMessage): Boolean {
        return requestMessageTypes.any { it.isInstance(element) }
    }
}
