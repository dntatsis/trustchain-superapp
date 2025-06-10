package nl.tudelft.trustchain.offlineeuro.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class RegisteredUserManagerTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }

    private val group = BilinearGroup()
    private val registeredUserManager = RegisteredUserManager(null, group, driver)

    @Before
    fun before() {
        registeredUserManager.clearAllRegisteredUsers()
    }

    @Test
    fun addAndRetrieveTest() {
        val name = "Tester"
        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey).immutable

        val registrationResult = registeredUserManager.addRegisteredUser(name, publicKey)
        Assert.assertTrue("The registration should be successful", registrationResult)

        val findByName = registeredUserManager.getRegisteredUserByName(name)!!
        Assert.assertEquals("The found public key should be equal", publicKey, findByName.publicKey)

        val findByPublicKey = registeredUserManager.getRegisteredUserByPublicKey(publicKey)!!
        Assert.assertEquals("The found name should be equal", name, findByPublicKey.name)

        val unregisteredName = "NotRegistered"
        val unregisteredPrivateKey = group.getRandomZr()
        val unregisteredPublicKey = group.g.powZn(unregisteredPrivateKey).immutable

        val notFoundByName = registeredUserManager.getRegisteredUserByName(unregisteredName)
        Assert.assertNull("No user should be found", notFoundByName)

        val notFoundByPublicKey = registeredUserManager.getRegisteredUserByPublicKey(unregisteredPublicKey)
        Assert.assertNull("No user should be found", notFoundByPublicKey)

        val allUsers = registeredUserManager.getAllRegisteredUsers()
        Assert.assertEquals("There should only be one user", 1, allUsers.size)
    }
}
