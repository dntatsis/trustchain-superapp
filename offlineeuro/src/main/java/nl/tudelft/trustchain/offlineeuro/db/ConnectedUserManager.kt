package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.ConnectedUsersQueries
import nl.tudelft.trustchain.offlineeuro.entity.ConnectedUser

/**
 * An overlay for the *ConnectedUsers* table.
 *
 * This class can be used to interact with the database.
 *
 * @property Context the context of the application, can be null, if a driver is given.
 * @property driver the driver of the database, can be used to pass a custom driver
 */
class ConnectedUserManager(
    context: Context? = null,
    driver: SqlDriver? = null
) {
    private val resolvedDriver = driver ?: AndroidSqliteDriver(Database.Schema, context!!, "connected_users.db")
    private val database: Database = Database(resolvedDriver)
    private val queries: ConnectedUsersQueries = database.connectedUsersQueries

     private val connectedUserMapper = {
            id: Long,
            name: String,
            secretShare: ByteArray
        ->
        ConnectedUser(
            id,
            name,
            secretShare
        )
    }

    /**
     * Creates the ConnectedUser table if it not yet exists.
     */
    init {
        queries.createConnectedUserTable()
        queries.clearAllConnectedUsers()
    }

    /**
     * Tries to add a new [ConnectedUser] to the table.
     *
     * @param user the user that should be registered. Its id will be omitted.
     * @return true iff registering the user is successful.
     */
    fun addConnectedUser(
        userName: String,
        secretShare: ByteArray
    ): Boolean {
        queries.addUser(
            userName,
            secretShare        )
        return true
    }

    /**
     * Gets a [ConnectedUser] by its [name].
     *
     * @param name the name of the [ConnectedUser]
     * @return the [ConnectedUser] with the [name] or null if the user does not exist.
     */
    fun getConnectedUserByName(name: String): ConnectedUser? {
        return queries.getUserByName(name, connectedUserMapper)
            .executeAsOneOrNull()
    }

    /**
     * Gets a [ConnectedUser] by its [publicKey].
     *
     * @param publicKey the public key of the [ConnectedUser]
     * @return the [ConnectedUser] with the [publicKey] or null if the user does not exist.
     */
    fun getConnectedUserBysecretShare(secretShare: ByteArray): ConnectedUser? {
        return queries.getUserBysecretShare(secretShare, connectedUserMapper)
            .executeAsOneOrNull()
    }

    fun getAllConnectedUsers(): List<ConnectedUser> {
        return queries.getAllConnectedUsers(connectedUserMapper).executeAsList()
    }

    /**
     * Clears all the [ConnectedUser]s from the table.
     */
    fun clearAllConnectedUsers() {
        queries.clearAllConnectedUsers()
    }
}

