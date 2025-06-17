package nl.tudelft.trustchain.offlineeuro.ui
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import nl.tudelft.trustchain.offlineeuro.enums.Role

class AllRolesFragment : OfflineEuroBaseFragment(R.layout.fragment_all_roles_home) {
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    private lateinit var ttpList: MutableList<TTP>
    private lateinit var bank: Bank
    private lateinit var user: User

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
        val n = 2
        ttpList = MutableList(n) { index ->
            val ttpName = if (index == 0) "TTP" else "TTP $index"
            if (index == 0)
                REGTTP(
                name = ttpName,
                group = group,
                communicationProtocol = iPV8CommunicationProtocol,
                context = context,
                onDataChangeCallback = onTTPDataChangeCallback)
            else
                TTP(
                name = ttpName,
                group = group,
                communicationProtocol = iPV8CommunicationProtocol,
                context = context,
                onDataChangeCallback = onTTPDataChangeCallback)
        }
        // ttp = TTP("TTP", group, iPV8CommunicationProtocol, context, onDataChangeCallback = onTTPDataChangeCallback)

        bank = Bank("Bank", group, iPV8CommunicationProtocol, context, depositedEuroManager, onDataChangeCallback = onBankDataChangeCallBack)
        bank.group = ttpList[0].group
        bank.crs = ttpList[0].crs
        bank.generateKeyPair()

        iPV8CommunicationProtocol.participant = ttpList[0]
        ttpList[0].registerUser(bank.name, bank.publicKey)
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(bank.name, Role.Bank, bank.publicKey, null))

        user =
            User(
                "TestUser",
                group,
                context,
                null,
                iPV8CommunicationProtocol,
                runSetup = false,
                onDataChangeCallback = onUserDataChangeCallBack,
                Identification_Value = "my_secret"
            )
        user.group = ttpList[0].group
        user.crs = ttpList[0].crs

        ParticipantHolder.ttp = ttpList
        ParticipantHolder.bank = bank
        ParticipantHolder.user = user

        //ttpList[0].registerUser(user.name, user.publicKey)
        //iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(user.name, Role.User, user.publicKey, null))
        //iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttpList[0].name, Role.REG_TTP, ttpList[0].publicKey, null))

        //ttpList.subList(1, ttpList.size).forEach { ttp ->
        //    iPV8CommunicationProtocol.addressBookManager.insertAddress(
        //        Address(ttp.name, Role.TTP, ttp.publicKey, null)
        //    )
        //}
        print(iPV8CommunicationProtocol.addressBookManager.getAllAddresses())

        prepareButtons(view)
        setTTPAsChild(view)
        //updateUserList(view)
    }

    private fun prepareButtons(view: View) {
        val ttpButton = view.findViewById<Button>(R.id.all_roles_set_ttp)
        val bankButton = view.findViewById<Button>(R.id.all_roles_set_bank)
        val userButton = view.findViewById<Button>(R.id.all_roles_set_user)
        ttpButton.setOnClickListener {
            ttpButton.isEnabled = false
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
            userButton.isEnabled = false
            setUserAsChild()
        }
    }

    private fun setTTPAsChild(view: View) {
        iPV8CommunicationProtocol.participant = ttpList[0]
        val ttpFragment = REGTTPHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, ttpFragment)
            .commit()
        Toast.makeText(context, "Switched to TTP", Toast.LENGTH_SHORT).show()
        ttpFragment.updateUserList(view)
    }

    private fun setBankAsChild() {
        iPV8CommunicationProtocol.participant = bank
        val bankFragment = BankHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, bankFragment)
            .commit()
        Toast.makeText(context, "Switched to Bank", Toast.LENGTH_SHORT).show()
    }

    private fun setUserAsChild() {
        iPV8CommunicationProtocol.participant = user
        val userFragment = UserHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, userFragment)
            .commit()
        Toast.makeText(context, "Switched to User", Toast.LENGTH_SHORT).show()
    }

    private val onBankDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                CallbackLibrary.bankCallback(requireContext(), message,iPV8CommunicationProtocol, requireView(), bank)
            }
        }
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::user.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                /* CallbackLibrary.userCallback(
                    context,
                    message,
                    requireView(),
                    iPV8CommunicationProtocol,
                    user,this
                ) */
            }
        }
    }

    private val onTTPDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttpList.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                // TODO: figure out how to deal with added TTPhome context!
                //CallbackLibrary.ttpCallback(context, message, requireView(), ttpList[0], this)
            }
        }
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttpList[0].getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }
}
