package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.Assert
import org.junit.Test

class TTPRegistrationPayloadTest {
    @Test
    fun serializeAndDeserializeTest() {
        val name = "NameForUserThatIsTryingToRegister"
        val publicKeyBytes = "NotAPublicKeyButJustSomeBytes".toByteArray()

        val serializedPayload = TTPRegistrationPayload(name, publicKeyBytes, Role.User).serialize()
        val deserializedPayload = TTPRegistrationPayload.deserialize(serializedPayload).first
        val deserializedName = deserializedPayload.userName
        val deserializedPublicKey = deserializedPayload.publicKey

        Assert.assertEquals("The name should be equal", name, deserializedName)
        Assert.assertArrayEquals("The public key bytes should be equal", publicKeyBytes, deserializedPublicKey)
    }
}
