package nl.tudelft.trustchain.offlineeuro.community.message

enum class CommunityMessageType {
    AddressMessage,
    AddressRequestMessage,

    RegistrationMessage,

    GroupDescriptionCRSRequestMessage,
    GroupDescriptionCRSReplyMessage,

    TTPRegistrationMessage,

    TTPConnectionMessage,

    BlindSignatureRandomnessRequestMessage,
    BlindSignatureRandomnessReplyMessage,

    BlindSignatureRequestMessage,
    BlindSignatureReplyMessage,

    TransactionRandomnessRequestMessage,
    TransactionRandomnessReplyMessage,

    TransactionMessage,
    TransactionResultMessage,

    FraudControlRequestMessage,
    FraudControlReplyMessage,
}
