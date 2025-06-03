package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import nl.tudelft.trustchain.offlineeuro.entity.misc.randomNameGenerator

class TTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: TTP
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.ttp != null) {
            ttp = ParticipantHolder.ttp!![0]
        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            ttp = TTP(
                name = "TTP " + randomNameGenerator.randomName,
                group = group,
                communicationProtocol = iPV8CommunicationProtocol,
                context = context,
                onDataChangeCallback = onDataChangeCallback
            )


        }
        val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
        welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", ttp.name)

        onDataChangeCallback(null)
        val ttpInfo: List<Pair<String, Boolean>> = listOf(ttp.name to true)
        refreshOtherTTPsView(view, ttpInfo)
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp, this)
            }
        }
    }

     fun refreshOtherTTPsView(view: View, allTTPs: List<Pair<String, Boolean>>) {
        val otherTtpContainer = view.findViewById<LinearLayout>(R.id.ttp_home_other_ttp_list)

        // Clear previous views to avoid duplicates
        otherTtpContainer.removeAllViews()

        val otherTtps = allTTPs.filter { it.first != ttp.name && it.first.startsWith("TTP") }

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
