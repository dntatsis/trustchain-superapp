package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPConnectionPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.ConnectedUserManager
import nl.tudelft.trustchain.offlineeuro.ui.BankHomeFragment
import nl.tudelft.trustchain.offlineeuro.ui.BaseTTPFragment
import nl.tudelft.trustchain.offlineeuro.ui.OfflineEuroBaseFragment
import nl.tudelft.trustchain.offlineeuro.ui.TTPHomeFragment


open class TTP(
    name: String = "TTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    val connectedUserManager: ConnectedUserManager = ConnectedUserManager(context),
    onDataChangeCallback: ((String?) -> Unit)? = null,
    var connected_Users: MutableList<Pair<String,ByteArray>> = mutableListOf(),
    val active: Boolean = true,
) : Participant(communicationProtocol, name, onDataChangeCallback) {
        var regGroup: BilinearGroup = BilinearGroup(PairingTypes.FromFileCopy, context = context)
        var crsMap: Map<Element, Element>
        init {
        communicationProtocol.participant = this
        this.group = group
        val generatedCRS = CRSGenerator.generateCRSMap(group)
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
        generateKeyPair()
    }
//    lateinit var adrBook: AddressBookManager

    fun getSharefromTTP(name: String): ByteArray? {
        communicationProtocol.participant = this
        for (i in this.connected_Users) {
            if (i.first == name) {
                return i.second
            }
        }
        return null
    }

    fun registerUser(
        name: String,
        publicKey: Element
    ): Boolean {
        val result = registeredUserManager.addRegisteredUser(name, publicKey)
        onDataChangeCallback?.invoke("Registered $name")
        Log.i("adr_invoke TTP","invoked TTP")
        return result
    }
        suspend fun setup(){
            setUp(false)
        }
        fun connectUser(
            name: String,
            secretShare: ByteArray
        ): Boolean {
            val result = connectedUserManager.addConnectedUser(name, secretShare)

            val index = connected_Users.indexOfFirst { it.first == name } // add user and share to the connected list
            if (index != -1) {
                connected_Users[index] = name to secretShare  // update
            } else {
                connected_Users.add(name to secretShare)      // add
            }
            onDataChangeCallback?.invoke("secret_share_recv by $name")
            return result
        }

    fun getRegisteredUsers(): List<RegisteredUser> {
        Log.d("RegisteredUsers for $name: ", registeredUserManager.getAllRegisteredUsers().toString())
        return registeredUserManager.getAllRegisteredUsers()
    }
    fun getConnectedUsers(): List<ConnectedUser> {
        return connectedUserManager.getAllConnectedUsers()
    }
    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        TODO("Not yet implemented")
    }
    fun getSecondCrs(): CRSBytes {
        val elems = crsMap.values

        return CRS(
            g = elems.elementAt(0),
            u = elems.elementAt(1),
            gPrime = elems.elementAt(2),
            uPrime = elems.elementAt(3),
            h = elems.elementAt(4),
            v = elems.elementAt(5),
            hPrime = elems.elementAt(6),
            vPrime = elems.elementAt(7)).toCRSBytes()
    }

    fun getUserFromProof(grothSahaiProof: GrothSahaiProof): String? { // return name of user
        val crsExponent = crsMap[crs.u]
        val publicKey =
            grothSahaiProof.c1.powZn(crsExponent!!.mul(-1)).mul(grothSahaiProof.c2).immutable

        val user = (communicationProtocol as IPV8CommunicationProtocol).addressBookManager.getAllAddresses()
            .firstOrNull { it.publicKey == publicKey }

        val userName= user?.name

        return userName

    }

    fun getUserFromProofs(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof
    ): Pair<String?,ByteArray?> {
        val firstUser = getUserFromProof(firstProof)
        val secondUser = getUserFromProof(secondProof)
        if (firstUser != null && firstUser == secondUser) {

            return Pair(firstUser,getSharefromTTP(firstUser))
        } else {
//            onDataChangeCallback?.invoke("Invalid fraud request received!")
//            "No double spending detected"
            return Pair(null,null)
        }
    }

    override suspend fun reset() {
        registeredUserManager.clearAllRegisteredUsers()
    }
}

    class REGTTP (
    name: String = "REGTTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    connectedUserManager: ConnectedUserManager = ConnectedUserManager(context),
    onDataChangeCallback: ((String?) -> Unit)? = null

) : TTP(name,group,communicationProtocol,context,registeredUserManager,connectedUserManager,onDataChangeCallback)
