package nl.tudelft.trustchain.offlineeuro

import android.util.Log
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.ipv8.Peer
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.ByteArrayPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.ConnectedUserManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.TransactionResult
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.Wallet
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger

class SystemTest {
    // Setup the TTP
    private val group: BilinearGroup = BilinearGroup(PairingTypes.A)
    private lateinit var ttp: TTP
    private lateinit var ttpCommunity: OfflineEuroCommunity
    private lateinit var crs: CRS
    private val userList = hashMapOf<User, OfflineEuroCommunity>()
    private lateinit var bank: Bank
    private lateinit var bankCommunity: OfflineEuroCommunity
    private var i = 0

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

    @Before
    fun setup() {
        // Initiate
        createTTP()
        createBank()
        val firstProofCaptor = argumentCaptor<ByteArray>()
        val secondProofCaptor = argumentCaptor<ByteArray>()
        `when`(bankCommunity.sendFraudControlRequest(firstProofCaptor.capture(), secondProofCaptor.capture(), any())).then {
            val firstProofBytes = firstProofCaptor.lastValue
            val secondProofBytes = secondProofCaptor.lastValue

            val peerMock = Mockito.mock(Peer::class.java)
            val fraudControlRequestMessage = FraudControlRequestMessage(firstProofBytes, secondProofBytes, peerMock)

            val fraudControlResultCaptor = argumentCaptor<ByteArray>()
            `when`(ttpCommunity.sendFraudControlReply(fraudControlResultCaptor.capture(), any())).then {
                val replyMessage = FraudControlReplyMessage(fraudControlResultCaptor.lastValue)
                bankCommunity.messageList.add(replyMessage)
            }

            ttpCommunity.messageList.add(fraudControlRequestMessage)
        }
    }

    @Test
    fun withdrawSpendDepositDoubleSpendDepositTest() {
        val user = createTestUser()

        // Assert that the group descriptions and crs are equal
        Assert.assertEquals("The group descriptions should be equal", bank.group, user.group)
        Assert.assertEquals("The group descriptions should be equal", bank.crs, user.crs)
        Assert.assertEquals("The group descriptions should be equal", ttp.crs, user.crs)

        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        addMessageToList(user, bankAddressMessage)
        bankCommunity.messageList.add(bankAddressMessage)
        val digitalEuro = withdrawDigitalEuro(user, bank.name)

        // Validations on the wallet
        val allWalletEntries = user.wallet.getAllWalletEntriesToSpend()
        Assert.assertEquals("There should only be one Euro", 1, allWalletEntries.size)

        val walletEntry = allWalletEntries[0]
        Assert.assertEquals("That should be the withdrawn Euro", digitalEuro, walletEntry.digitalEuro)

        val computedTheta1 = user.group.g.powZn(walletEntry.t.mul(-1))
        Assert.assertEquals("The first theta should be correct", digitalEuro.firstTheta1, computedTheta1)
        Assert.assertNull("The walletEntry should not have a previous transaction", walletEntry.transactionSignature)

        val user2 = createTestUser()
        addMessageToList(user2, bankAddressMessage)

        val user2AddressMessage = AddressMessage(user2.name, Role.User, user2.publicKey.toBytes(), user2.name.toByteArray())
        addMessageToList(user, user2AddressMessage)

        // First Spend
        spendEuro(user, user2)

        // Deposit
        spendEuro(user2, bank, "Deposit was successful!")

        // Prepare double spend
        val user3 = createTestUser()
        addMessageToList(user3, bankAddressMessage)

        val user3AddressMessage = AddressMessage(user3.name, Role.User, user3.publicKey.toBytes(), user3.name.toByteArray())
        addMessageToList(user, user3AddressMessage)

        // Double Spend
        spendEuro(user, user3, doubleSpend = true)

        val firstUserCaptor = argumentCaptor<ByteArray>()
        val secondUserCaptor = argumentCaptor<ByteArray>()
        val userPeer = Mockito.mock(Peer::class.java)
        `when`(bankCommunity.sendFraudControlRequest(firstUserCaptor.capture(), secondUserCaptor.capture(), any())).then {
            ttpCommunity.messageList.add(FraudControlRequestMessage(firstUserCaptor.lastValue, secondUserCaptor.lastValue, userPeer))
            val fraudControlReply = FraudControlReplyMessage("test123".toByteArray())
            bankCommunity.messageList.add(fraudControlReply)
        }

        // Deposit double spend Euro
        spendEuro(user3, bank, "Double spending detected: User secret: test123")
    }

    @Test
    fun getManyBlindSignatures() {
        val user = createTestUser()
        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        addMessageToList(user, bankAddressMessage)
        bankCommunity.messageList.add(bankAddressMessage)
        for (i in 0 until 50)
            withdrawDigitalEuro(user, bank.name)
    }

    private fun withdrawDigitalEuro(
        user: User,
        bankName: String
    ): DigitalEuro {
        // Prepare mock elements
        val byteArrayCaptor = argumentCaptor<ByteArray>()
        val challengeCaptor = argumentCaptor<BigInteger>()
        val userPeer = Mockito.mock(Peer::class.java)

        val userCommunity = userList[user]!!
        val publicKeyBytes = user.publicKey.toBytes()

        // Request the randomness
        `when`(userCommunity.getBlindSignatureRandomness(any(), any())).then {
            val randomnessRequestMessage = BlindSignatureRandomnessRequestMessage(publicKeyBytes, userPeer)
            bankCommunity.messageList.add(randomnessRequestMessage)

            verify(bankCommunity, atLeastOnce()).sendBlindSignatureRandomnessReply(byteArrayCaptor.capture(), any())
            val givenRandomness = byteArrayCaptor.lastValue

            val randomnessReplyMessage = BlindSignatureRandomnessReplyMessage(givenRandomness)
            addMessageToList(user, randomnessReplyMessage)

            // Request the signature
            `when`(userCommunity.getBlindSignature(challengeCaptor.capture(), any(), any())).then {
                val challenge = challengeCaptor.lastValue
                val signatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKeyBytes, userPeer)
                bankCommunity.messageList.add(signatureRequestMessage)

                verify(bankCommunity, atLeastOnce()).sendBlindSignature(challengeCaptor.capture(), any())
                val signature = challengeCaptor.lastValue

                val signatureMessage = BlindSignatureReplyMessage(signature)
                addMessageToList(user, signatureMessage)
            }
        }

        val withdrawnEuro = user.withdrawDigitalEuro(bankName)

        // User must make two requests
        verify(userCommunity, atLeastOnce()).getBlindSignatureRandomness(publicKeyBytes, bank.name.toByteArray())
        verify(userCommunity, atLeastOnce()).getBlindSignature(any(), eq(publicKeyBytes), eq(bank.name.toByteArray()))

        // Bank must respond twice
        verify(bankCommunity, atLeastOnce()).sendBlindSignatureRandomnessReply(any(), eq(userPeer))
        verify(bankCommunity, atLeastOnce()).sendBlindSignature(any(), eq(userPeer))

        // The euro must be valid
        Assert.assertTrue(
            "The signature should be valid for the user",
            Schnorr.verifySchnorrSignature(withdrawnEuro.signature, bank.publicKey, user.group)
        )
        print("Valid ${i++}")
        Assert.assertEquals("There should be no proofs", arrayListOf<GrothSahaiProof>(), withdrawnEuro.proofs)

        return withdrawnEuro
    }

    private fun spendEuro(
        sender: User,
        receiver: Participant,
        expectedResult: String = TransactionResult.VALID_TRANSACTION.description,
        doubleSpend: Boolean = false
    ) {
        val senderCommunity = userList[sender]!!
        val receiverCommunity =
            if (receiver.name == bank.name) {
                bankCommunity
            } else {
                userList[receiver]!!
            }
        val spenderPeer = Mockito.mock(Peer::class.java)
        val randomizationElementsCaptor = argumentCaptor<RandomizationElementsBytes>()
        val transactionDetailsCaptor = argumentCaptor<TransactionDetailsBytes>()
        val transactionResultCaptor = argumentCaptor<String>()

        `when`(senderCommunity.getTransactionRandomizationElements(sender.publicKey.toBytes(), receiver.name.toByteArray())).then {
            val requestMessage = TransactionRandomizationElementsRequestMessage(sender.publicKey.toBytes(), spenderPeer)
            receiverCommunity.messageList.add(requestMessage)
            verify(receiverCommunity).sendTransactionRandomizationElements(randomizationElementsCaptor.capture(), eq(spenderPeer))
            val randomizationElementsBytes = randomizationElementsCaptor.lastValue
            val randomizationElementsMessage = TransactionRandomizationElementsReplyMessage(randomizationElementsBytes)
            senderCommunity.messageList.add(randomizationElementsMessage)

            // To send the transaction details
            `when`(
                senderCommunity.sendTransactionDetails(
                    eq(sender.publicKey.toBytes()),
                    eq(receiver.name.toByteArray()),
                    transactionDetailsCaptor.capture()
                )
            ).then {
                val transactionDetailsBytes = transactionDetailsCaptor.lastValue
                val transactionMessage = TransactionMessage(sender.publicKey.toBytes(), transactionDetailsBytes, spenderPeer)
                receiverCommunity.messageList.add(transactionMessage)
                verify(receiverCommunity, atLeastOnce()).sendTransactionResult(transactionResultCaptor.capture(), any())
                val result = transactionResultCaptor.lastValue
                val transactionResultMessage = TransactionResultMessage(result)
                senderCommunity.messageList.add(transactionResultMessage)
            }
        }

        val transactionResult =
            if (doubleSpend) {
                sender.doubleSpendDigitalEuroTo(receiver.name)
            } else {
                sender.sendDigitalEuroTo(receiver.name)
            }
        Assert.assertEquals(expectedResult, transactionResult)
    }

    fun createTestUser(): User {
        // Start with a random group
        val addressBookManager = createAddressManager(group)
        val walletManager = WalletManager(null, group, "wallet_test", createDriver())

        // Add the community for later access
        val userName = "User${userList.size}"
        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        `when`(community.messageList).thenReturn(communicationProtocol.messageList)
        val user = User(userName, group, null, walletManager, communicationProtocol, runSetup = false)
        user.generateKeyPair()
        user.wallet = Wallet(user.privateKey, user.publicKey, walletManager)
        user.crs = crs
        user.group = group
        userList[user] = community
        ttp.registerUser(user.name, user.publicKey)
        return user
    }

    private fun createTTP() {
        val addressBookManager = createAddressManager(group)
        val registeredUserManager = RegisteredUserManager(null, group, createDriver())

        ttpCommunity = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, ttpCommunity)

        `when`(ttpCommunity.messageList).thenReturn(communicationProtocol.messageList)
        val connectedUserManager = mock<ConnectedUserManager>()
        whenever(connectedUserManager.addConnectedUser(any(), any())).thenReturn(true)

        ttp = REGTTP("TTP", group, communicationProtocol, null, registeredUserManager, connectedUserManager=connectedUserManager)
        crs = ttp.crs
        communicationProtocol.participant = ttp
    }

    private fun createBank() {
        val addressBookManager = createAddressManager(group)
        val depositedEuroManager = DepositedEuroManager(null, group, createDriver())

        bankCommunity = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, bankCommunity)

        `when`(bankCommunity.messageList).thenReturn(communicationProtocol.messageList)

        bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, runSetup = false)
        bank.generateKeyPair()
        bank.crs = crs
        addressBookManager.insertAddress(Address(ttp.name, Role.TTP, ttp.publicKey, "SomeTTPPubKey".toByteArray()))
        ttp.registerUser(bank.name, bank.publicKey)
    }

    private fun createAddressManager(group: BilinearGroup): AddressBookManager {
        val addressBookManager = AddressBookManager(null, group, createDriver())
        return addressBookManager
    }

    private fun addMessageToList(
        user: User,
        message: ICommunityMessage
    ) {
        val community = userList[user]
        community!!.messageList.add(message)
    }

    private fun prepareCommunityMock(): OfflineEuroCommunity {
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        return community
    }

    private fun createDriver(): JdbcSqliteDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }
    }
}
