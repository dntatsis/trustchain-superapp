package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.AddressBookQueries
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.enums.Role

class AddressBookManager(
    context: Context?,
    group: BilinearGroup,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "address_book.db"),
) : OfflineEuroManager(group, driver) {
    private val queries: AddressBookQueries = database.addressBookQueries
    private val addressMapper = {
            name: String,
            type: Long,
            publicKey: ByteArray,
            peerPublicKey: ByteArray?,
        ->
        Address(
            name,
            Role.fromLong(type),
            group.pairing.g1.newElementFromBytes(publicKey).immutable,
            peerPublicKey
        )
    }

    init {
        queries.createAddressBookTable()
        queries.clearAddressBookTable()
    }

    fun insertAddress(address: Address) {
        val (name, type, publicKey, peerPublicKey) = address
        queries.insertAddress(
            name,
            type.ordinal.toLong(),
            publicKey.toBytes(),
            peerPublicKey
        )
    }

    fun getAddressByName(name: String): Address {
        val all = queries.getAllAddresses(addressMapper).executeAsList()
        Log.i("adr", "All entries: $all")
        return queries.getAddressByName(
            name,
            addressMapper
        ).executeAsOne()
    }

    fun getAllAddresses(): List<Address> {
        return queries.getAllAddresses(addressMapper).executeAsList()
    }

    fun clear() {
        return queries.clearAddressBookTable()
    }
}
