package nl.tudelft.trustchain.offlineeuro.ui
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
    ) { // TODO: this file needs complete restructuring, callback functions have been redefined, same with participant holder
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Flexible role"
        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        val depositedEuroManager = DepositedEuroManager(context, group)

        // create N TTPs, first one being an registration TTP
        fun getKDistinctValues(k: Int, n: Int): List<Int> {
            require(k <= n) { "k must be less than or equal to n for distinct values." }
            return (1..n).shuffled().take(k)
        }
        val inactive = getKDistinctValues(User.maximum_shares - User.minimum_shares,User.maximum_shares)
        Log.i("adr inactive",inactive.toString())
        ttpList = MutableList(User.maximum_shares) { index ->
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
        ParticipantHolder.regttp = ttpList[0] as REGTTP
        ParticipantHolder.ttp = ttpList.drop(1).toMutableList()

        // create the ttp fragments as well
        ttpFragmentList = List(User.maximum_shares) { index ->
            if (index == 0)
                REGTTPHomeFragment()
            else
                TTPHomeFragment(count = index - 1) // subtract one because regttp doesnt count
        }
        for (i in 0 until User.maximum_shares){ // set callbacks and add to address book
            if (i == 0){
                ttpList[i].onDataChangeCallback = onREGTTPDataChangeCallback(ttpList[i] as REGTTP, ttpFragmentList[i] as REGTTPHomeFragment)
                iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttpList[i].name, Role.REG_TTP, ttpList[i].publicKey, null))
            }
            else {
                ttpList[i].onDataChangeCallback = onTTPDataChangeCallback(ttpList[i], ttpFragmentList[i] as TTPHomeFragment)
                iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttpList[i].name, Role.TTP, ttpList[i].publicKey, null))
                ttpList[i].crs = ttpList[0].crs
                ttpList[i].crsMap = ttpList[0].crsMap
            }

        }

        // Create bank, bank fragment

        bank = Bank("Bank", group, iPV8CommunicationProtocol, context, depositedEuroManager, onDataChangeCallback = null)
        bank.group = ttpList[0].group
        bank.crs = ttpList[0].crs
        bank.generateKeyPair()
        ParticipantHolder.bank = bank
        bank.isAllRoles = true
        bankFragment = BankHomeFragment()
        bank.onDataChangeCallback = onBankDataChangeCallBack(bank,bankFragment)
        iPV8CommunicationProtocol.participant = ttpList[0] // iPV8CommunicationProtocol.participant represents the active entity

        // register bank to REGTTP
        ttpList[0].registerUser(bank.name, bank.publicKey)
        // add to addressbook
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(bank.name, Role.Bank, bank.publicKey, null))

        Log.i("adr_reg","registered bank")
        activity?.title = "User"

        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        userList = mutableListOf<User>()

        // create each user
        var connectedSuccessfully = true;
        lifecycleScope.launch {
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
        ParticipantHolder.user = userList

        // create user fragments
        userFragment = List(usersNum) { index ->
            UserHomeFragment(count = index)
        }

        // set up individual group, wallet, crs, datachangecallback for users

        for (i in 0 until usersNum) {

            userList[i].wallet =
                Wallet(userList[i].privateKey, userList[i].publicKey, userList[i].walletManager!!)
            userList[i].group = ttpList[0].group
            userList[i].crs = ttpList[0].crs
            iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(userList[i].name, Role.User, userList[i].publicKey, null))

            userList[i].onDataChangeCallback = onUserDataChangeCallBack(userList[i],userFragment[i])
        }

        if (!connectedSuccessfully) return



        for (i in 0 until usersNum) { // register all users
            ttpList[0].registerUser(userList[i].name, userList[i].publicKey)

        }
        //iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(user.name, Role.User, user.publicKey, null))
        //iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttpList[0].name, Role.REG_TTP, ttpList[0].publicKey, null))

        //ttpList.subList(1, ttpList.size).forEach { ttp ->
        //    iPV8CommunicationProtocol.addressBookManager.insertAddress(
        //        Address(ttp.name, Role.TTP, ttp.publicKey, null)
        //    )
        //}

        prepareButtons(view)
        setTTPAsChild(view)
        //updateUserList(view)
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
