package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP

class REGTTPHomeFragment : BaseTTPFragment(R.layout.fragment_reg_home) {
    private lateinit var regttp: REGTTP

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("adr_am i null?",(ParticipantHolder.regttp == null).toString())
        if (ParticipantHolder.regttp != null) {
            regttp = ParticipantHolder.regttp!!
            iPV8CommunicationProtocol = regttp.communicationProtocol as IPV8CommunicationProtocol
            regttp.isAllRoles = true

        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            regttp = REGTTP(
                name = "TTP",
                group = group,
                communicationProtocol = iPV8CommunicationProtocol,
                context = context,
                onDataChangeCallback = onDataChangeCallback
            )

        }
        setWelcomeText(view,regttp.name)
        onDataChangeCallback(null)
        val ttpInfo: MutableList<Pair<String, Boolean>> = mutableListOf(regttp.name to true)
        refreshOtherTTPsView(view, regttp.name, ttpInfo)

        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {
            lifecycleScope.launch {
                if(!regttp.isAllRoles){
                    iPV8CommunicationProtocol.scopePeers()
                }
                else{
                    onDataChangeCallback("Refresh!")
                }
            }
        }
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::regttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.regttpCallback(context, message, requireView(), iPV8CommunicationProtocol, regttp,this)
            }
        }
    }

    fun updateUserList(view: View) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = regttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    fun refreshRegisteredUsersView(
        view: View,
        registeredUsers: List<Triple<String, String, String>> // Triple<UserID, Name, PublicKey>
    ) {
        val userListContainer = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list)
        if(userListContainer == null){
            return
        }
        // Remove all children except the header (assumed to be the first child)
        if (userListContainer.childCount > 1) {
            userListContainer.removeViews(1, userListContainer.childCount - 1)
        }

        if (registeredUsers.isEmpty()) {
            val emptyView = TextView(view.context).apply {
                text = "No registered users/banks found"
                setPadding(0, 10, 0, 10)
            }
            userListContainer.addView(emptyView)
            return
        }

        for ((userId, name, publicKey) in registeredUsers) {
            val row = LinearLayout(view.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val userIdView = TextView(view.context).apply {
                text = userId
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f)
                gravity = Gravity.CENTER
            }

            val nameView = TextView(view.context).apply {
                text = name
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f)
                gravity = Gravity.CENTER
            }

            val publicKeyView = TextView(view.context).apply {
                text = publicKey.take(10) + "..." // Show only the first 10 characters
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                gravity = Gravity.CENTER
            }

            row.addView(userIdView)
            row.addView(nameView)
            row.addView(publicKeyView)

            userListContainer.addView(row)
        }
    }

}
