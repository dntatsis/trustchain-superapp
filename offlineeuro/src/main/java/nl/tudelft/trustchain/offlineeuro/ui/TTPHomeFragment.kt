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
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class TTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: MutableList<TTP>
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("adr", "starting1...")

        if (ParticipantHolder.ttp != null) {
            ttp = ParticipantHolder.ttp!!
        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            val n = 3
            ttp = mutableListOf()
            for (index in 0 until n) {
                val ttpName = if (index == 0) "TTP" else "TTP $index"
                if (index == 0)
                    ttp.add(
                        REGTTP(
                            name = ttpName,
                            group = group,
                            communicationProtocol = iPV8CommunicationProtocol,
                            context = context,
                            onDataChangeCallback = onDataChangeCallback
                        )
                    )
                else
                    ttp.add(
                        TTP(
                            name = ttpName,
                            group = group,
                            communicationProtocol = iPV8CommunicationProtocol,
                            context = context,
                            onDataChangeCallback = onDataChangeCallback
                        )
                    )

            }
            Log.i("adr", "$ttp ending...")

        }
        onDataChangeCallback(null)
        val ttpInfo: List<Pair<String, Boolean>> = ttp.map { it.name to true }
        refreshOtherTTPsView(view, ttpInfo)
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp[0])
            }
        }
    }

    private fun refreshOtherTTPsView(view: View, allTTPs: List<Pair<String, Boolean>>) {
        val otherTtpContainer = view.findViewById<LinearLayout>(R.id.ttp_home_other_ttp_list)

        // Clear previous views to avoid duplicates
        otherTtpContainer.removeAllViews()

        // Filter out the main "TTP" (keep only others like "TTP 1", "TTP 2", etc.)
        val otherTtps = allTTPs.filter { it.first != "TTP" && it.first.startsWith("TTP") }

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
}
