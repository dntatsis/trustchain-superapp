package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.offlineeuro.communication.CRSTransformer
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProof
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.util.concurrent.Callable
import org.mockito.Mockito.mockStatic

class BankTest {
    private val ttpGroup = BilinearGroup(PairingTypes.FromFile)
    private val crsValue = CRSGenerator.generateCRSMap(ttpGroup)
    private val crs = crsValue.first
    private val crsMap = crsValue.second
    private val depositedEuroManager = Mockito.mock(DepositedEuroManager::class.java)

    lateinit var logMock: MockedStatic<Log>

    @Before
    fun mockAndroidLog() {
        logMock = mockStatic(Log::class.java)

        logMock.`when`<Int> { Log.i(any(), any()) }.thenReturn(0)
        logMock.`when`<Int> { Log.d(any(), any()) }.thenReturn(0)
    }

    @After
    fun closeAndroidLogMock() {
        logMock.close()
    }


    @Test
    fun initWithSetupTest() {
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        whenever(community.messageList).thenReturn(communicationProtocol.messageList)
        whenever(community.getGroupDescriptionAndCRS()).then {
            communicationProtocol.messageList.add(
                BilinearGroupCRSReplyMessage(
                    ttpGroup.toGroupElementBytes(),
                    crs.toCRSBytes(),
                    CRSTransformer.crsValues(crsMap),
                    AddressMessage("TTP", Role.REG_TTP, "SomeBytes".toByteArray(), "More Bytes".toByteArray())
                )
            )
        }
        val ttpAddress = Address("TTP", Role.REG_TTP, ttpGroup.generateRandomElementOfG(), "More Bytes".toByteArray())

        val publicKeyCaptor = argumentCaptor<ByteArray>()

        whenever(addressBookManager.getAddressByName("TTP")).thenReturn(ttpAddress)
        whenever(community.registerAtTTP(any(), publicKeyCaptor.capture(), any(), any())).then { }

        val bankName = "SomeBank"
        val bank = Bank(bankName, BilinearGroup(PairingTypes.FromFile), communicationProtocol, null, depositedEuroManager, runSetup = false)
        runBlocking{bank.setUp()}

        val capturedPKBytes = publicKeyCaptor.firstValue
        val capturedPK = ttpGroup.gElementFromBytes(capturedPKBytes)

        Assert.assertEquals(bankName, bank.name)
        Assert.assertEquals(ttpGroup, bank.group)
        Assert.assertEquals(crs, bank.crs)
        Assert.assertEquals(bank.publicKey, capturedPK)
        Assert.assertEquals(bank, communicationProtocol.participant)
    }

    @Test
    fun initWithoutSetUpTest() {
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        val bankName = "SomeOtherBank"
        val group = BilinearGroup(PairingTypes.FromFile)
        val bank = Bank(bankName, group, communicationProtocol, null, depositedEuroManager, false)

        verify(community, never()).getGroupDescriptionAndCRS()
        verify(community, never()).registerAtTTP(any(), any(), any(),any())
        Assert.assertEquals(group, bank.group)

        Assert.assertThrows(UninitializedPropertyAccessException::class.java) {
            bank.crs.toCRSBytes()
        }
    }

    @Test
    fun getBlindSignatureRandomnessTest() {
        val bank = getBank()
        val publicKey = ttpGroup.generateRandomElementOfG()

        val firstRandomness = bank.getBlindSignatureRandomness(publicKey)
        val secondRandomness = bank.getBlindSignatureRandomness(publicKey)

        Assert.assertEquals("The same randomness should be returned", firstRandomness, secondRandomness)

        val newPublicKey = ttpGroup.generateRandomElementOfG()
        val thirdRandomness = bank.getBlindSignatureRandomness(newPublicKey)

        Assert.assertNotEquals(firstRandomness, thirdRandomness)
    }

    @Test
    fun getBlindSignatureTest() {
        val bank = getBank()
        val publicKey = ttpGroup.generateRandomElementOfG()
        val firstRandomness = bank.getBlindSignatureRandomness(publicKey)
        val elementToSign = ttpGroup.generateRandomElementOfG().immutable.toBytes()
        val serialNumber = "TestSerialNumber"
        val bytesToSign = serialNumber.toByteArray() + elementToSign

        val blindedChallenge = Schnorr.createBlindedChallenge(firstRandomness, bytesToSign, bank.publicKey, ttpGroup)
        val blindSignature = bank.createBlindSignature(blindedChallenge.blindedChallenge, publicKey)
        val blindSchnorrSignature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        Assert.assertTrue(Schnorr.verifySchnorrSignature(blindSchnorrSignature, bank.publicKey, ttpGroup))

        val noRandomnessRequestedKey = ttpGroup.generateRandomElementOfG()
        val response = bank.createBlindSignature(blindedChallenge.blindedChallenge, noRandomnessRequestedKey)
        Assert.assertEquals("There should be no randomness found", BigInteger.ZERO, response)
    }

    @Test
    fun depositEuro_newEuro_successfullyDeposited() {
        // Arrange
        val group = ttpGroup
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        val bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, false)
        bank.crs = crs
        bank.generateKeyPair()

        val mockEuro = Mockito.mock(DigitalEuro::class.java)
        val publicKeyUser = group.generateRandomElementOfG()
        val euroSerial = "serial-123"
        Mockito.`when`(mockEuro.serialNumber).thenReturn(euroSerial)
        Mockito.`when`(mockEuro.proofs).thenReturn(arrayListOf())

        // No duplicates
        Mockito.`when`(depositedEuroManager.getDigitalEurosByDescriptor(mockEuro)).thenReturn(emptyList())

        // Act
        val result = bank.javaClass.getDeclaredMethod("depositEuro", DigitalEuro::class.java, Element::class.java)
            .apply { isAccessible = true }
            .invoke(bank, mockEuro, publicKeyUser) as String

        // Assert
        Assert.assertEquals("Deposit was successful!", result)
        Mockito.verify(depositedEuroManager).insertDigitalEuro(mockEuro)
        Assert.assertTrue(bank.depositedEuroLogger.any { it.first == euroSerial && !it.second })
    }

    @Test
    fun depositEuro_doubleSpending_detectedAndRecovered() {
        // Arrange
        val group = ttpGroup
        val communicationProtocol = Mockito.mock(IPV8CommunicationProtocol::class.java)

        val bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, false)
        bank.crs = crs
        bank.generateKeyPair()

        // Created two different proofs to simulate double-spending
        val proof1 = Mockito.mock(GrothSahaiProof::class.java)
        val proof2 = Mockito.mock(GrothSahaiProof::class.java)

        // DigitalEuro being deposited
        val newEuro = Mockito.mock(DigitalEuro::class.java)
        Mockito.`when`(newEuro.serialNumber).thenReturn("serial-123")
        Mockito.`when`(newEuro.proofs).thenReturn(arrayListOf(proof1))

        // Duplicating euro with same serial but different proof
        val duplicateEuro = Mockito.mock(DigitalEuro::class.java)
        Mockito.`when`(duplicateEuro.serialNumber).thenReturn("serial-123")
        Mockito.`when`(duplicateEuro.proofs).thenReturn(arrayListOf(proof2))

        // Simulating double spending by returning duplicate
        Mockito.`when`(depositedEuroManager.getDigitalEurosByDescriptor(newEuro))
            .thenReturn(listOf(duplicateEuro))

        val publicKeyUser = group.generateRandomElementOfG()

        // TTP response with user secret
        val fakeSecret = "RecoveredSecretFromTTP"
        val fraudResponse = FraudControlReplyMessage(fakeSecret.toByteArray())

        // Simulate fraud control returning a valid result
        Mockito.`when`(communicationProtocol.requestFraudControl(proof1, proof2))
            .thenReturn(mutableMapOf("TTP" to fraudResponse))

        // Act: use reflection to call private method
        val result = bank.javaClass.getDeclaredMethod("depositEuro", DigitalEuro::class.java, Element::class.java)
            .apply { isAccessible = true }
            .invoke(bank, newEuro, publicKeyUser) as String

        // Assert
        Assert.assertTrue(result.contains("Double spending detected"))
        Assert.assertTrue(result.contains(fakeSecret))
        Mockito.verify(depositedEuroManager).insertDigitalEuro(newEuro)
        Assert.assertTrue(bank.depositedEuroLogger.any { it.first == "serial-123" && it.second })
    }


    @Test
    fun depositEuro_doubleSpending_ttpUnreachable() {
        // Arrange
        val group = ttpGroup
        val communicationProtocol = Mockito.mock(IPV8CommunicationProtocol::class.java)

        val bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, false)
        bank.crs = crs
        bank.generateKeyPair()

        val proof1 = Mockito.mock(GrothSahaiProof::class.java)
        val proof2 = Mockito.mock(GrothSahaiProof::class.java)

        val newEuro = Mockito.mock(DigitalEuro::class.java)
        Mockito.`when`(newEuro.serialNumber).thenReturn("serial-456")
        Mockito.`when`(newEuro.proofs).thenReturn(arrayListOf(proof1))

        val duplicateEuro = Mockito.mock(DigitalEuro::class.java)
        Mockito.`when`(duplicateEuro.serialNumber).thenReturn("serial-456")
        Mockito.`when`(duplicateEuro.proofs).thenReturn(arrayListOf(proof2))

        Mockito.`when`(depositedEuroManager.getDigitalEurosByDescriptor(newEuro))
            .thenReturn(listOf(duplicateEuro))

        val publicKeyUser = group.generateRandomElementOfG()

        // Simulating TTP being unreachable
        Mockito.`when`(communicationProtocol.requestFraudControl(proof1, proof2))
            .thenThrow(RuntimeException("TTP unreachable"))

        // Act
        val result = bank.javaClass.getDeclaredMethod("depositEuro", DigitalEuro::class.java, Element::class.java)
            .apply { isAccessible = true }
            .invoke(bank, newEuro, publicKeyUser) as String

        // Assert
        Assert.assertTrue(result.contains("Found double spending proofs, but TTP is unreachable"))
        Mockito.verify(depositedEuroManager).insertDigitalEuro(newEuro)
        Assert.assertTrue(bank.depositedEuroLogger.any { it.first == "serial-456" && it.second })
    }

    fun getBank(): Bank {
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        val bankName = "Bank"
        val group = ttpGroup
        val bank = Bank(bankName, group, communicationProtocol, null, depositedEuroManager, false)
        bank.crs = crs
        bank.generateKeyPair()

        return bank
    }
}
