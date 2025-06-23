package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.WalletQueries
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import nl.tudelft.trustchain.offlineeuro.entity.WalletEntry

class WalletManager(
    context: Context?,
    private val group: BilinearGroup,
    walletName: String,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "$walletName.db"),
) : OfflineEuroManager(group, driver) {
    private val queries: WalletQueries = database.walletQueries
    private val walletEntryMapper = {
            serialNumber: String,
            firstTheta: ByteArray,
            signature: ByteArray,
            previousProofs: ByteArray?,
            secretT: ByteArray,
            transactionSignature: ByteArray?,
            timesSpent: Long
        ->
        WalletEntry(
            DigitalEuro(
                serialNumber,
                group.gElementFromBytes(firstTheta),
                deserializeSchnorr(signature)!!,
                deserializeGSP(previousProofs)
            ),
            group.zrElementFromBytes(secretT),
            deserializeSchnorr(transactionSignature),
            timesSpent
        )
    }

    /**
     * Creates the RegisteredUser table if it not yet exists.
     */
    init {
        queries.createWalletTable()
        queries.clearWalletTable()
    }

    /**
     * Tries to add a new [RegisteredUser] to the table.
     *
     * @param user the user that should be registered. Its id will be omitted.
     * @return true iff registering the user is successful.
     */
    fun insertWalletEntry(walletEntry: WalletEntry): Boolean {
        val digitalEuro = walletEntry.digitalEuro

        queries.insertWalletEntry(
            digitalEuro.serialNumber,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            serialize(digitalEuro.proofs),
            walletEntry.t.toBytes(),
            serialize(walletEntry.transactionSignature)
        )
        return true
    }

    fun getAllDigitalWalletEntries(): List<WalletEntry> {
        return queries.getAllWalletEntries(walletEntryMapper).executeAsList()
    }

    fun getWalletEntriesToSpend(): List<WalletEntry> {
        return queries.getWalletEntriesToSpend(walletEntryMapper).executeAsList()
    }

    fun getNumberOfWalletEntriesToSpend(number: Int): List<WalletEntry> {
        return queries.getNumberOfWalletEntriesToSpend(number.toLong(), walletEntryMapper).executeAsList()
    }

    fun getNumberOfWalletEntriesToDoubleSpend(number: Int): List<WalletEntry> {
        return queries.getNumberOfWalletEntriesToDoubleSpend(number.toLong(), walletEntryMapper).executeAsList()
    }

    fun getWalletEntriesToDoubleSpend(): List<WalletEntry> {
        return queries.getWalletEntriesToDoubleSpend(walletEntryMapper).executeAsList()
    }

    fun incrementTimesSpent(digitalEuro: DigitalEuro) {
        queries.incrementTimesSpent(
            digitalEuro.serialNumber,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            serialize(digitalEuro.proofs)
        )
    }

    fun getWalletEntryByDigitalEuro(digitalEuro: DigitalEuro): WalletEntry? {
        return queries.getWalletEntryByDescriptor(
            digitalEuro.serialNumber,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            walletEntryMapper
        ).executeAsOneOrNull()
    }

    fun clearWalletEntries() {
        queries.clearWalletTable()
    }
}
