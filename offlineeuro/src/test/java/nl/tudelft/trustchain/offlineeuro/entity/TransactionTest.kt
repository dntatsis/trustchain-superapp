package nl.tudelft.trustchain.offlineeuro.entity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.Peer
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.ConnectedUserManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.math.BigInteger
import kotlin.math.floor

class TransactionTest {
    // Setup the TTP
    private val group: BilinearGroup = BilinearGroup(PairingTypes.A)
    private lateinit var crs: CRS
    private lateinit var ttp: TTP

    private val registrationNameCaptor = argumentCaptor<String>()
    private val publicKeyCaptor = argumentCaptor<ByteArray>()
    private val userList = hashMapOf<User, OfflineEuroCommunity>()
    private lateinit var bank: Bank
    private lateinit var bankCommunity: OfflineEuroCommunity
    private var i = 0

    @Before
    fun setup() {
        // Initiate
        createTTP()
        createBank()
    }

    @Test
    fun transactionWithoutProofsTest() {
        val user = createTestUser()
        withdrawDigitalEuro(user, bank.name)
        val walletEntry = user.wallet!!.getWalletEntryToSpend()!!

        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey)
        val randomT = group.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
        val transactionDetails =
            Transaction.createTransaction(
                privateKey,
                publicKey,
                walletEntry,
                randomizationElements,
                group,
                crs,
            )

        Assert.assertTrue("The transaction should be valid", Transaction.validate(transactionDetails, bank.publicKey, group, crs).valid)
    }

    @Test
    fun invalidProof() {
        val user = createTestUser()
        withdrawDigitalEuro(user, bank.name)
        val walletEntry = addProofsToDigitalEuro(user.wallet!!.getWalletEntryToSpend()!!, 10)
        val transactionDetails = walletEntryToTransactionDetails(walletEntry)
        val randomProofNr = floor(Math.random() * transactionDetails.digitalEuro.proofs.size).toInt()
        val randomProof = transactionDetails.digitalEuro.proofs[randomProofNr]
        val newProof =
            GrothSahaiProof(
                group.generateRandomElementOfG(),
                randomProof.c2,
                randomProof.d1,
                randomProof.d2,
                randomProof.theta1,
                randomProof.theta2,
                randomProof.pi1,
                randomProof.pi2,
                randomProof.target
            )
        transactionDetails.digitalEuro.proofs[randomProofNr] = newProof
        val verificationResult = Transaction.validate(transactionDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", verificationResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_PROOF_IN_CHAIN.description, verificationResult.description)
    }

    @Test
    fun invalidBankSignature() {
        val user = createTestUser()
        val firstEuro = withdrawDigitalEuro(user, bank.name)
        val walletEntry = user.wallet!!.getWalletEntryToSpend()!!
        val secondEuro = withdrawDigitalEuro(user, bank.name)

        // Try with an invalid signature
        val fakeSignature =
            SchnorrSignature(BigInteger("1230284023820194821"), firstEuro.signature.encryption, firstEuro.signature.signedMessage)
        val invalidEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, fakeSignature, firstEuro.proofs)
        val invalidWalletEntry = WalletEntry(invalidEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val invalidSignatureDetails = walletEntryToTransactionDetails(invalidWalletEntry)
        val invalidSignatureResult = Transaction.validate(invalidSignatureDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", invalidSignatureResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, invalidSignatureResult.description)

        // Try with a different signature
        val fakeEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, secondEuro.signature, firstEuro.proofs)
        val fakeWalletEntry = WalletEntry(fakeEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val transactionDetails = walletEntryToTransactionDetails(fakeWalletEntry)
        val verificationResult = Transaction.validate(transactionDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", verificationResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, verificationResult.description)

        // Try with a different T
        val invalidT = group.getRandomZr()
        val invalidTWalletEntry = WalletEntry(walletEntry.digitalEuro, invalidT, walletEntry.transactionSignature, walletEntry.timesSpent)
        val invalidTDetails = walletEntryToTransactionDetails(invalidTWalletEntry)
        val invalidTResult = Transaction.validate(invalidTDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", invalidTResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_TS_RELATION_BANK_SIGNATURE.description, invalidTResult.description)
    }

    @Test
    fun invalidBankSignature2() {
        val user = createTestUser()
        val firstEuro = withdrawDigitalEuro(user, bank.name)
        val walletEntry = user.wallet!!.getWalletEntryToSpend()!!
        val secondEuro = withdrawDigitalEuro(user, bank.name)

        // Try with an invalid signature
        val fakeSignature =
            SchnorrSignature(BigInteger("1230284023820194821"), firstEuro.signature.encryption, firstEuro.signature.signedMessage)
        val invalidEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, fakeSignature, firstEuro.proofs)
        val invalidWalletEntry = WalletEntry(invalidEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val invalidSignatureDetails = walletEntryToTransactionDetails(invalidWalletEntry)
        val invalidSignatureResult = Transaction.validate(invalidSignatureDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", invalidSignatureResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, invalidSignatureResult.description)

        // Try with a different signature
        val fakeEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, secondEuro.signature, firstEuro.proofs)
        val fakeWalletEntry = WalletEntry(fakeEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val transactionDetails = walletEntryToTransactionDetails(fakeWalletEntry)
        val verificationResult = Transaction.validate(transactionDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", verificationResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, verificationResult.description)

        // Try with a different T
        val invalidT = group.getRandomZr()
        val invalidTWalletEntry = WalletEntry(walletEntry.digitalEuro, invalidT, walletEntry.transactionSignature, walletEntry.timesSpent)
        val invalidTDetails = walletEntryToTransactionDetails(invalidTWalletEntry)
        val invalidTResult = Transaction.validate(invalidTDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", invalidTResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_TS_RELATION_BANK_SIGNATURE.description, invalidTResult.description)
    }

    @Test
    fun invalidBankSignatureMultipleProofs() {
        val user = createTestUser()
        val firstEuro = withdrawDigitalEuro(user, bank.name)
        val walletEntry = addProofsToDigitalEuro(user.wallet!!.getWalletEntryToSpend()!!, 2)
        val secondEuro = withdrawDigitalEuro(user, bank.name)

        // Try with an invalid signature
        val fakeSignature =
            SchnorrSignature(BigInteger("1230284023820194821"), firstEuro.signature.encryption, firstEuro.signature.signedMessage)
        val invalidEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, fakeSignature, firstEuro.proofs)
        val invalidWalletEntry = WalletEntry(invalidEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val invalidSignatureDetails = walletEntryToTransactionDetails(invalidWalletEntry)
        val invalidSignatureResult = Transaction.validate(invalidSignatureDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", invalidSignatureResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, invalidSignatureResult.description)

        // Try with a different signature
        val fakeEuro = DigitalEuro(firstEuro.serialNumber, firstEuro.firstTheta1, secondEuro.signature, firstEuro.proofs)
        val fakeWalletEntry = WalletEntry(fakeEuro, walletEntry.t, walletEntry.transactionSignature, walletEntry.timesSpent)
        val transactionDetails = walletEntryToTransactionDetails(fakeWalletEntry)
        val verificationResult = Transaction.validate(transactionDetails, bank.publicKey, group, crs)
        Assert.assertFalse("The transaction should be invalid", verificationResult.valid)
        Assert.assertEquals(TransactionResult.INVALID_BANK_SIGNATURE.description, verificationResult.description)
    }

    private fun walletEntryToTransactionDetails(walletEntry: WalletEntry): TransactionDetails {
        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey)
        val randomT = group.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
        return Transaction.createTransaction(
            privateKey,
            publicKey,
            walletEntry,
            randomizationElements,
            group,
            crs,
        )
    }

    private fun addProofsToDigitalEuro(
        walletEntry: WalletEntry,
        numberOfProofs: Int
    ): WalletEntry {
        var entry = walletEntry
        for (i: Int in 0 until numberOfProofs) {
            val privateKey = group.getRandomZr()
            val publicKey = group.g.powZn(privateKey)
            val randomT = group.getRandomZr()
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
            val transactionDetails =
                Transaction.createTransaction(
                    privateKey,
                    publicKey,
                    entry,
                    randomizationElements,
                    group,
                    crs,
                )
            Assert.assertTrue("The transaction should be valid", Transaction.validate(transactionDetails, bank.publicKey, group, crs).valid)
            entry = detailsToWalletEntry(transactionDetails, randomT)
        }

        return entry
    }

    private fun withdrawDigitalEuro(
        user: User,
        bankName: String
    ): DigitalEuro {
        // Assert that the group descriptions and crs are equal
        Assert.assertEquals("The group descriptions should be equal", bank.group, user.group)
        Assert.assertEquals("The group descriptions should be equal", bank.crs, user.crs)

        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        addMessageToList(user, bankAddressMessage)
        // TODO MAKE THIS UNNECESSARY
        bankCommunity.messageList.add(bankAddressMessage)
        // Prepare mock elements
        val byteArrayCaptor = argumentCaptor<ByteArray>()
        val challengeCaptor = argumentCaptor<BigInteger>()
        val userPeer = Mockito.mock(Peer::class.java)

        val userCommunity = userList[user]!!
        val publicKeyBytes = user.publicKey.toBytes()

        // Request the randomness
        Mockito.`when`(userCommunity.getBlindSignatureRandomness(any(), any())).then {
            val randomnessRequestMessage = BlindSignatureRandomnessRequestMessage(publicKeyBytes, userPeer)
            bankCommunity.messageList.add(randomnessRequestMessage)

            verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignatureRandomnessReply(byteArrayCaptor.capture(), any())
            val givenRandomness = byteArrayCaptor.lastValue

            val randomnessReplyMessage = BlindSignatureRandomnessReplyMessage(givenRandomness)
            addMessageToList(user, randomnessReplyMessage)

            // Request the signature
            Mockito.`when`(userCommunity.getBlindSignature(challengeCaptor.capture(), any(), any()))
                .then {
                    val challenge = challengeCaptor.lastValue
                    val signatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKeyBytes, userPeer)
                    bankCommunity.messageList.add(signatureRequestMessage)

                    verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignature(challengeCaptor.capture(), any())
                    val signature = challengeCaptor.lastValue

                    val signatureMessage = BlindSignatureReplyMessage(signature)
                    addMessageToList(user, signatureMessage)
                }
        }

        val withdrawnEuro = user.withdrawDigitalEuro(bankName)

        // User must make two requests
        verify(userCommunity, Mockito.atLeastOnce()).getBlindSignatureRandomness(publicKeyBytes, bank.name.toByteArray())
        verify(userCommunity, Mockito.atLeastOnce()).getBlindSignature(any(), eq(publicKeyBytes), eq(bank.name.toByteArray()))

        // Bank must respond twice
        verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignatureRandomnessReply(any(), eq(userPeer))
        verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignature(any(), eq(userPeer))

        // The euro must be valid
        Assert.assertTrue(
            "The signature should be valid for the user",
            Schnorr.verifySchnorrSignature(withdrawnEuro.signature, bank.publicKey, user.group)
        )

        Assert.assertEquals("There should be no proofs", arrayListOf<GrothSahaiProof>(), withdrawnEuro.proofs)

        return withdrawnEuro
    }

    fun createTestUser(): User {
        val addressBookManager = createAddressManager(group)
        val walletManager = WalletManager(null, group, createDriver())

        // Add the community for later access
        val userName = "User${userList.size}"
        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        Mockito.`when`(community.messageList).thenReturn(communicationProtocol.messageList)
        val user = User(userName, group, null, walletManager, communicationProtocol, runSetup = false)
        user.crs = ttp.crs
        userList[user] = community
        return user
    }

    fun detailsToWalletEntry(
        transactionDetails: TransactionDetails,
        t: Element
    ): WalletEntry {
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)

        val transactionSignature = transactionDetails.theta1Signature
        return WalletEntry(digitalEuro, t, transactionSignature)
    }

    private fun createTTP() {
        val addressBookManager = createAddressManager(group)
        val registeredUserManager = RegisteredUserManager(null, group, createDriver())
        val connectedUserManager = ConnectedUserManager(null, createDriver())

        val ttpCommunity = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, ttpCommunity)

        Mockito.`when`(ttpCommunity.messageList).thenReturn(communicationProtocol.messageList)
        ttp = TTP("TTP", group, communicationProtocol, null, registeredUserManager, connectedUserManager)
        crs = ttp.crs
        communicationProtocol.participant = ttp
    }

    private fun createBank() {
        val addressBookManager = createAddressManager(group)
        val depositedEuroManager = DepositedEuroManager(null, group, createDriver())

        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        Mockito.`when`(community.messageList).thenReturn(communicationProtocol.messageList)
        bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, runSetup = false)
        bank.crs = crs
        bank.generateKeyPair()
        bankCommunity = community
        communicationProtocol.participant = bank
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
