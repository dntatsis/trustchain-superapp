package nl.tudelft.trustchain.offlineeuro.ui
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.Wallet
import nl.tudelft.trustchain.offlineeuro.enums.Role

class AllRolesFragment : OfflineEuroBaseFragment(R.layout.fragment_all_roles_home) {
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    private lateinit var ttpList: MutableList<TTP>
    private lateinit var bank: Bank
    private lateinit var userList: MutableList<User>

    private lateinit var ttpFragmentList: List<BaseTTPFragment>
    private lateinit var bankFragment: BankHomeFragment
    private lateinit var userFragment: List<UserHomeFragment>
    private var inc_user = 0 // counts visits to user fragments
    private var inc_ttp = 0 // counts visits to ttp fragments
    private val usersNum = 2
    private var isInitialized = false
    private var currentChildFragment: OfflineEuroBaseFragment? = null
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Flexible role"
        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        val depositedEuroManager = DepositedEuroManager(context, group)

        val setupttp = setupTTPs(group)
        ttpList = setupttp.first
        ttpFragmentList = setupttp.second

        // Create bank, bank fragment
        val setupbank =  setupBank(group, depositedEuroManager, ttpList[0] as REGTTP, iPV8CommunicationProtocol)

        bank = setupbank.first
        bankFragment = setupbank.second

        // Create users, user fragments
        val setupusers = setupUsers(group, requireContext(), iPV8CommunicationProtocol, usersNum, ttpList)
        if (setupusers == null) {
            // Handle failure here
            return
        }
        userList = setupusers.first
        userFragment = setupusers.second
        prepareButtons(view)
        setTTPAsChild(view)
    }

    private fun setupTTPs(group: BilinearGroup): Pair<MutableList<TTP>, List<BaseTTPFragment>> {

        val inactive = (1..User.maximum_shares).shuffled().take(0)
        val ttpList = MutableList(User.maximum_shares) { index ->
            val ttpName = if (index == 0) "TTP" else "TTP $index"
            val active = index !in inactive
            if (index == 0)
                REGTTP(
                    name = ttpName,
                    group = group,
                    communicationProtocol = iPV8CommunicationProtocol,
                    context = context,
                    onDataChangeCallback = null
                )
            else
                TTP(
                    name = ttpName,
                    group = group,
                    communicationProtocol = iPV8CommunicationProtocol,
                    context = context,
                    onDataChangeCallback = null,
                    active = active
                )
        }

        val ttpFragments = List(User.maximum_shares) { index ->
            if (index == 0) REGTTPHomeFragment()
            else TTPHomeFragment(count = index - 1)
        }

        ParticipantHolder.regttp = ttpList[0] as REGTTP
        ParticipantHolder.ttp = ttpList.drop(1).toMutableList()

        for (i in ttpList.indices) {
            val ttp = ttpList[i]
            val fragment = ttpFragments[i]
            when (ttp) {
                is REGTTP -> {
                    ttp.onDataChangeCallback = onREGTTPDataChangeCallback(ttp, fragment as REGTTPHomeFragment)
                    iPV8CommunicationProtocol.addressBookManager.insertAddress(
                        Address(ttp.name, Role.REG_TTP, ttp.publicKey, null)
                    )
                }

                else -> {
                    ttp.onDataChangeCallback =
                        onTTPDataChangeCallback(ttp, fragment as TTPHomeFragment)
                    iPV8CommunicationProtocol.addressBookManager.insertAddress(
                        Address(ttp.name, Role.TTP, ttp.publicKey, null)
                    )
                    // share CRS from REGTTP
                    ttp.crs = ttpList[0].crs
                    ttp.crsMap = (ttpList[0] as REGTTP).crsMap
                }
            }
        }

        return Pair(ttpList.toMutableList(), ttpFragments)
    }

    private fun setupBank(
        group: BilinearGroup,
        depositedEuroManager: DepositedEuroManager,
        regTTP: REGTTP,
        communicationProtocol: IPV8CommunicationProtocol
    ): Pair<Bank, BankHomeFragment> {
        val bank = Bank("Bank", group, communicationProtocol, context, depositedEuroManager, onDataChangeCallback = null)

        // Set group and CRS from REGTTP
        bank.group = regTTP.group
        bank.crs = regTTP.crs
        bank.generateKeyPair()
        bank.isAllRoles = true

        val bankFragment = BankHomeFragment()
        bank.onDataChangeCallback = onBankDataChangeCallBack(bank, bankFragment)

        // Register bank in global holder and address book
        ParticipantHolder.bank = bank
        regTTP.registerUser(bank.name, bank.publicKey)
        communicationProtocol.addressBookManager.insertAddress(
            Address(bank.name, Role.Bank, bank.publicKey, null)
        )

        // Assign active participant (so IPV8 messages get routed correctly)
        communicationProtocol.participant = regTTP

        return Pair(bank, bankFragment)
    }


    fun setupUsers(
        group: BilinearGroup,
        context: Context,
        iPV8CommunicationProtocol: IPV8CommunicationProtocol,
        usersNum: Int,
        ttpList: List<TTP>
    ): Pair<MutableList<User>, List<UserHomeFragment>>? { // returns null if connection failed

        activity?.title = "User"

        // Initialize community and user list
        val community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val userList = mutableListOf<User>()

        var connectedSuccessfully = true

        // Create each user asynchronously
        lifecycleScope.launch  {
            repeat(usersNum) { index ->
                try {
                    val user = User(
                        "TestUser$index",
                        group,
                        context,
                        null,
                        iPV8CommunicationProtocol,
                        runSetup = true,
                        onDataChangeCallback = null,
                        Identification_Value = "my_secret$index"
                    )
                    userList.add(user)
                } catch (e: Throwable) {
                    Toast.makeText(
                        context,
                        "${e.message}: Failed to connect to a TTP",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("adr_user", "User creation failed", e)
                    connectedSuccessfully = false
                }
            }
        }

        // Return null if any user failed to connect
        if (!connectedSuccessfully) return null

        ParticipantHolder.user = userList

        // Create user fragments
        val userFragment = List(usersNum) { index -> UserHomeFragment(count = index) }

        // Setup wallet, group, crs, address book, and callbacks for each user
        for (i in 0 until usersNum) {
            userList[i].wallet = Wallet(userList[i].privateKey, userList[i].publicKey, userList[i].walletManager!!)
            userList[i].group = ttpList[0].group
            userList[i].crs = ttpList[0].crs

            iPV8CommunicationProtocol.addressBookManager.insertAddress(
                Address(userList[i].name, Role.User, userList[i].publicKey, null)
            )

            userList[i].onDataChangeCallback = onUserDataChangeCallBack(userList[i], userFragment[i])
        }

        // Register all users to the REGTTP
        for (i in 0 until usersNum) {
            ttpList[0].registerUser(userList[i].name, userList[i].publicKey)
        }

        return Pair(userList, userFragment)
    }

    private fun prepareButtons(view: View) {
        val ttpButton = view.findViewById<Button>(R.id.all_roles_set_ttp)
        val bankButton = view.findViewById<Button>(R.id.all_roles_set_bank)
        val userButton = view.findViewById<Button>(R.id.all_roles_set_user)
        ttpButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = true
            userButton.isEnabled = true
            setTTPAsChild(view)
        }

        bankButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = false
            userButton.isEnabled = true
            setBankAsChild()
        }

        userButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = true
            userButton.isEnabled = true
            setUserAsChild()
        }
    }

    private fun setTTPAsChild(view: View) {
        iPV8CommunicationProtocol.participant = ttpList[inc_ttp % User.maximum_shares]

        currentChildFragment = ttpFragmentList[inc_ttp % User.maximum_shares]

        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, ttpFragmentList[inc_ttp % User.maximum_shares])
            .commit()
        Toast.makeText(context, "Switched to TTP", Toast.LENGTH_SHORT).show()
        inc_ttp += 1
    }

    private fun setBankAsChild() {
        iPV8CommunicationProtocol.participant = bank
        currentChildFragment = bankFragment
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, bankFragment)
            .commit()
        Toast.makeText(context, "Switched to Bank", Toast.LENGTH_SHORT).show()
    }

    private fun setUserAsChild() {
        iPV8CommunicationProtocol.participant = userList[inc_user % usersNum]
        currentChildFragment = userFragment[inc_user % usersNum]
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, userFragment[inc_user % usersNum])
            .commit()
        Toast.makeText(context, "Switched to User", Toast.LENGTH_SHORT).show()
        inc_user += 1
    }

    private fun onBankDataChangeCallBack(
        bank: Bank,
        fragment: BankHomeFragment
    ): (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            CallbackLibrary.bankCallback(
                requireContext(),
                message,
                iPV8CommunicationProtocol,
                requireView(),
                bank
            )
        }
    }

    private fun onUserDataChangeCallBack(
        user: User,
        fragment: UserHomeFragment
    ): (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            CallbackLibrary.userCallback(
                requireContext(),
                message,
                requireView(),
                iPV8CommunicationProtocol,
                user,
                fragment
            )
        }
    }


    private fun onREGTTPDataChangeCallback(
        regttp: REGTTP,
        fragment: REGTTPHomeFragment
    ): (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            CallbackLibrary.regttpCallback(
                requireContext(),
                message,
                requireView(),
                iPV8CommunicationProtocol,
                regttp,
                fragment
            )
        }
    }
    private fun onTTPDataChangeCallback(
        ttp: TTP,
        fragment: TTPHomeFragment
    ): (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            CallbackLibrary.ttpCallback(
                requireContext(),
                message,
                iPV8CommunicationProtocol,
                requireView(),
                ttp,
                fragment
            )
        }
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttpList[0].getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }
}
