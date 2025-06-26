package nl.tudelft.trustchain.offlineeuro.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class ConnectedUserManagerTest {

    private lateinit var connectedUserManager: ConnectedUserManager

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }
        connectedUserManager = ConnectedUserManager(null, driver)
        connectedUserManager.clearAllConnectedUsers()
    }

    @Test
    fun addAndRetrieveUserTest() {
        val name = "Alice"
        val secretShare = UUID.randomUUID().toString().toByteArray()

        val added = connectedUserManager.addConnectedUser(name, secretShare)
        Assert.assertTrue("User should be added successfully", added)

        val foundByName = connectedUserManager.getConnectedUserByName(name)
        Assert.assertNotNull("User should be found by name", foundByName)
        Assert.assertEquals("Names should match", name, foundByName!!.name)
        Assert.assertArrayEquals("Secret shares should match", secretShare, foundByName.secretShare)

        val foundBySecret = connectedUserManager.getConnectedUserBysecretShare(secretShare)
        Assert.assertNotNull("User should be found by secret share", foundBySecret)
        Assert.assertEquals("Names should match", name, foundBySecret!!.name)
        Assert.assertArrayEquals("Secret shares should match", secretShare, foundBySecret.secretShare)
    }

    @Test
    fun retrieveNonExistentUserTest() {
        val notFoundByName = connectedUserManager.getConnectedUserByName("NonExistent")
        Assert.assertNull("No user should be found by non-existent name", notFoundByName)

        val fakeSecret = "FakeSecret".toByteArray()
        val notFoundBySecret = connectedUserManager.getConnectedUserBysecretShare(fakeSecret)
        Assert.assertNull("No user should be found by fake secret share", notFoundBySecret)
    }

    @Test
    fun getAllUsersTest() {
        val users = listOf(
            "Bob" to UUID.randomUUID().toString().toByteArray(),
            "Charlie" to UUID.randomUUID().toString().toByteArray()
        )

        for ((name, share) in users) {
            connectedUserManager.addConnectedUser(name, share)
        }

        val allUsers = connectedUserManager.getAllConnectedUsers()
        Assert.assertEquals("All users should be returned", 2, allUsers.size)

        val names = allUsers.map { it.name }
        Assert.assertTrue("All names should be present", names.containsAll(users.map { it.first }))
    }

    @Test
    fun clearAllUsersTest() {
        connectedUserManager.addConnectedUser("TempUser", "temp".toByteArray())

        val allUsersBefore = connectedUserManager.getAllConnectedUsers()
        Assert.assertFalse("User should be added", allUsersBefore.isEmpty())

        connectedUserManager.clearAllConnectedUsers()

        val allUsersAfter = connectedUserManager.getAllConnectedUsers()
        Assert.assertTrue("All users should be cleared", allUsersAfter.isEmpty())
    }
}
