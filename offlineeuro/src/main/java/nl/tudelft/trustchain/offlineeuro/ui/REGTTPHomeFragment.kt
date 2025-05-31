package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP

class REGTTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_reg_home) {
    private lateinit var regttp: REGTTP
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.regttp != null) {
            regttp = ParticipantHolder.regttp!!
        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            val n = 3
            regttp = REGTTP(
                name = "TTP",
                group = group,
                communicationProtocol = iPV8CommunicationProtocol,
                context = context,
                onDataChangeCallback = onDataChangeCallback
            )

        }
        val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
        welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", regttp.name)

        onDataChangeCallback(null)
        val ttpInfo: MutableList<Pair<String, Boolean>> = mutableListOf(regttp.name to true)
        refreshOtherTTPsView(view, ttpInfo)

        view.findViewById<Button>(R.id.ttp_reg_sync_user_button).setOnClickListener {
            iPV8CommunicationProtocol.scopePeers()
        }
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::regttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.regttpCallback(context, message, requireView(), regttp,this)
            }
        }
    }
    fun refreshRegisteredUsersView(
        view: View,
        registeredUsers: List<Triple<String, String, String>> // Triple<UserID, Name, PublicKey>
    ) {
        val userListContainer = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list)

        // Remove all children except the header (assumed to be the first child)
        if (userListContainer.childCount > 1) {
            userListContainer.removeViews(1, userListContainer.childCount - 1)
        }

        if (registeredUsers.isEmpty()) {
            val emptyView = TextView(view.context).apply {
                text = "No registered users found"
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
     public fun refreshOtherTTPsView(view: View, allTTPs: List<Pair<String, Boolean>>) {
        val otherTtpContainer = view.findViewById<LinearLayout>(R.id.ttp_home_other_ttp_list)

        // Clear previous views to avoid duplicates
        otherTtpContainer.removeAllViews()

        val otherTtps = allTTPs.filter { it.first != regttp.name && it.first.startsWith("TTP") }

        if (otherTtps.isEmpty()) {
            // Show placeholder if no other TTPs found
            val emptyView = TextView(view.context).apply {
                text = "No other TTPs available"
                setPadding(0, 10, 0, 10)
            }
            otherTtpContainer.addView(emptyView)
            return
        }

        // Add a TextView for each other TTP with location label
        for ((ttpName, isLocal) in otherTtps) {
            val ttpTextView = TextView(view.context).apply {
                text = "$ttpName (${if (isLocal) "Local" else "Remote"})"
                textSize = 16f
                setTextColor(ContextCompat.getColor(view.context, android.R.color.black))
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            otherTtpContainer.addView(ttpTextView)
        }

    }
    public fun refreshSecretSharesView(view: View, connectedUsers: List<Pair<String, ByteArray>>) {
        val userListContainer = view.findViewById<LinearLayout>(R.id.tpp_home_secret_shared_user_list)

        // Remove all children except the header (assumed to be the first child)
        if (userListContainer.childCount > 1) {
            userListContainer.removeViews(1, userListContainer.childCount - 1)
        }

        if (connectedUsers.isEmpty()) {
            val emptyView = TextView(view.context).apply {
                text = "No secret shares available"
                setPadding(0, 10, 0, 10)
            }
            userListContainer.addView(emptyView)
            return
        }

        for ((name, secretShare) in connectedUsers) {
            val row = LinearLayout(view.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val nameView = TextView(view.context).apply {
                text = name
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f)
                gravity = android.view.Gravity.CENTER
            }

            val shareView = TextView(view.context).apply {
                text = secretShare.toString()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                gravity = android.view.Gravity.CENTER
            }

            row.addView(nameView)
            row.addView(shareView)
            userListContainer.addView(row)
        }
    }

}
