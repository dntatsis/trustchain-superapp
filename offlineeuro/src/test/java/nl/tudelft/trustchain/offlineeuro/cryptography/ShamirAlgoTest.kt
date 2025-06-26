package nl.tudelft.trustchain.offlineeuro.cryptography;

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotSame
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import org.junit.Test;
import java.security.SecureRandom

class ShamirAlgoTest {

    @Test
    fun recoverSecretFromTwoPartsTest() {
        val secret = "this_is_my_private_id".toByteArray(Charsets.UTF_8)
        val scheme = Scheme(SecureRandom(), 3, 2)
        val parts = scheme.split(secret)
        val partialParts = parts.entries.take(2).associate { it.toPair() }
        val recovered = scheme.join(partialParts)
        val recoveredString = String(recovered, Charsets.UTF_8)
        assertEquals("this_is_my_private_id", recoveredString)
    }

    @Test
    fun recoverSecretFromOnePartFailsTest() {
        val secret = "this_is_my_private_id".toByteArray(Charsets.UTF_8)
        val scheme = Scheme(SecureRandom(), 3, 2)
        val parts = scheme.split(secret)
        val partialParts = parts.entries.take(1).associate { it.toPair() }
        val recovered = scheme.join(partialParts)
        val recoveredString = String(recovered, Charsets.UTF_8)
        assertNotSame("this_is_my_private_id", recoveredString)
    }
}
