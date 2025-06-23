package nl.tudelft.trustchain.offlineeuro.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.generateRandomBigInteger
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.WalletEntry
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class WalletManagerTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }

    private val upperBound = BigInteger("9999999999")
    private val group = BilinearGroup()
    private val walletManager = WalletManager(null, group, "wallet_test", driver)

    @Before
    fun before() {
        walletManager.clearWalletEntries()
    }

    private fun generateRandomSignature(): SchnorrSignature {
        return SchnorrSignature(
            generateRandomBigInteger(upperBound),
            generateRandomBigInteger(upperBound),
            UUID.randomUUID().toString().toByteArray()
        )
    }

    private fun getRandomWalletEntry(proofCount: Int): WalletEntry {
        val randomDigitalEuro =
            DigitalEuro(
                UUID.randomUUID().toString(),
                group.generateRandomElementOfG(),
                generateRandomSignature(),
                arrayListOf()
            )

        if (proofCount == 0) {
            return WalletEntry(randomDigitalEuro, group.getRandomZr(), null)
        }

        val signature = generateRandomSignature()
        for (i in 0 until proofCount) {
            val proofToAdd = generateRandomInvalidProof()
            randomDigitalEuro.proofs.add(proofToAdd)
        }
        return WalletEntry(randomDigitalEuro, group.getRandomZr(), signature)
    }

    @Test
    fun addAndRetrieveNewEuroTest() {
        val walletEntry = getRandomWalletEntry(0)
        walletManager.insertWalletEntry(walletEntry)

        val retrievedEntry = walletManager.getWalletEntryByDigitalEuro(walletEntry.digitalEuro)
        Assert.assertEquals("The inserted and retrieved WalletEntry should be equal", walletEntry, retrievedEntry)

        val allEntries = walletManager.getAllDigitalWalletEntries()
        Assert.assertEquals("There should be on WalletEntry inserted", 1, allEntries.count())
        Assert.assertEquals("There the WalletEntry should be equal", walletEntry, allEntries.first())

        val allUnspendEntries = walletManager.getWalletEntriesToSpend()
        Assert.assertEquals("The inserted wallet entry should not be spend", 1, allUnspendEntries.count())
        Assert.assertEquals("There the WalletEntry not should be spend", walletEntry, allUnspendEntries.first())

        val allSpentEntries = walletManager.getWalletEntriesToDoubleSpend()
        Assert.assertTrue("There should be no WalletEntry to double spend", allSpentEntries.isEmpty())
    }

    @Test
    fun addAndRetrieveUsedEuro() {
        val walletEntry = getRandomWalletEntry(5)
        walletManager.insertWalletEntry(walletEntry)

        val retrievedEntry = walletManager.getWalletEntryByDigitalEuro(walletEntry.digitalEuro)
        Assert.assertEquals("The inserted and retrieved WalletEntry should be equal", walletEntry, retrievedEntry)

        val allEntries = walletManager.getAllDigitalWalletEntries()
        Assert.assertEquals("There should be one WalletEntry inserted", 1, allEntries.count())
        Assert.assertEquals("There the WalletEntry should be equal", walletEntry, allEntries.first())

        val allUnspentEntries = walletManager.getWalletEntriesToSpend()
        Assert.assertEquals("The inserted WalletEntry should not be spend", 1, allUnspentEntries.count())
        Assert.assertEquals("There the WalletEntry not should be spend", walletEntry, allUnspentEntries.first())

        val allSpentEntries = walletManager.getWalletEntriesToDoubleSpend()
        Assert.assertTrue("There should be no WalletEntry to double spend", allSpentEntries.isEmpty())

        // increment spend
        walletManager.incrementTimesSpent(walletEntry.digitalEuro)

        val retrievedSpentWalletEntry = walletManager.getWalletEntryByDigitalEuro(walletEntry.digitalEuro)
        Assert.assertNotEquals("The WalletEntries should not be equal", walletEntry, retrievedSpentWalletEntry)

        // Only the times spend should differ by one
        Assert.assertEquals("The Euro should be unchanged", walletEntry.digitalEuro, retrievedSpentWalletEntry?.digitalEuro)
        Assert.assertEquals("The used t should be unchanged", walletEntry.t, retrievedSpentWalletEntry?.t)
        Assert.assertEquals(
            "The signature should be unchanged",
            walletEntry.transactionSignature,
            retrievedSpentWalletEntry?.transactionSignature
        )
        Assert.assertEquals(
            "The times spent should be incremented with 1",
            walletEntry.timesSpent + 1,
            retrievedSpentWalletEntry?.timesSpent
        )

        val allEntriesAfterSpend = walletManager.getAllDigitalWalletEntries()
        Assert.assertEquals("There should still be one WalletEntry", 1, allEntriesAfterSpend.count())

        val allUnspentEntriesAfterSpend = walletManager.getWalletEntriesToSpend()
        Assert.assertEquals("The inserted WalletEntry should be spend", 0, allUnspentEntriesAfterSpend.count())

        val allSpentEntriesAfterSpend = walletManager.getWalletEntriesToDoubleSpend()
        Assert.assertEquals("There should be a single spent WalletEntry", 1, allSpentEntriesAfterSpend.count())
        Assert.assertEquals(
            "The spent wallet entry should be the same as the retrieved one",
            retrievedSpentWalletEntry,
            allSpentEntriesAfterSpend.first()
        )
    }

    @Test
    fun manyWalletEntriesTest() {
        val createdWalletEntries = arrayListOf<WalletEntry>()

        for (i: Int in 0 until 20) {
            val randomNumberOfProofs = (Math.random() * 10).toInt()
            val createdWalletEntry = getRandomWalletEntry(randomNumberOfProofs)
            walletManager.insertWalletEntry(createdWalletEntry)
            createdWalletEntries.add(createdWalletEntry)
        }

        val spendIndices = arrayListOf<Int>()
        // Spent a random amount of those entries
        for (i: Int in 0 until createdWalletEntries.size) {
            val walletEntry = createdWalletEntries[i]
            val shouldSpend = Math.random() > 0.5
            if (shouldSpend) {
                walletManager.incrementTimesSpent(walletEntry.digitalEuro)
                spendIndices.add(i)
            }
        }

        val notSpendIndices = (0 until createdWalletEntries.size).filterNot { x -> spendIndices.contains(x) }

        val notSpendEntries = walletManager.getWalletEntriesToSpend()
        val spendEntries = walletManager.getWalletEntriesToDoubleSpend()

        Assert.assertEquals("The number of spent tokens should be correct", spendIndices.size, spendEntries.size)
        Assert.assertEquals("The number of not spent tokens should be correct", notSpendIndices.size, notSpendEntries.size)

        val unspentTokens = notSpendIndices.map { createdWalletEntries[it] }
        Assert.assertTrue("The unspent token list should be correct", notSpendEntries.containsAll(unspentTokens))

        // Compare spend entries on digital euro only as times spend does not change for the expected list
        val spentEuros = spendIndices.map { createdWalletEntries[it].digitalEuro }
        val spentEntryEuros = spendEntries.map { it.digitalEuro }
        Assert.assertTrue("The spent token list should be correct", spentEuros.containsAll(spentEntryEuros))
    }

    private fun randomGElement(): Element {
        return group.generateRandomElementOfG()
    }

    private fun randomHElement(): Element {
        return group.generateRandomElementOfH()
    }

    private fun randomGTElement(): Element {
        return group.generateRandomElementOfGT()
    }

    private fun generateRandomInvalidProof(): GrothSahaiProof {
        return GrothSahaiProof(
            randomGElement(),
            randomGElement(),
            randomHElement(),
            randomHElement(),
            randomGElement(),
            randomGElement(),
            randomHElement(),
            randomHElement(),
            randomGTElement()
        )
    }
}
