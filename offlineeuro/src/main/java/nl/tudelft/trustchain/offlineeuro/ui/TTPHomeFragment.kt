package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.offlineeuro.entity.misc.randomNameGenerator

class TTPHomeFragment(val count: Int = -1) : BaseTTPFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: TTP
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.ttp != null) {
            ttp = ParticipantHolder.ttp!![count]
            iPV8CommunicationProtocol = ttp.communicationProtocol as IPV8CommunicationProtocol
            ttp.isAllRoles = true
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
            lifecycleScope.launch {
                ttp.setup() // exchange group with regttp - Requires REGTTP to be launched first!
            }
        }
        setWelcomeText(view,ttp.name)

        onDataChangeCallback(null)
        val ttpInfo: List<Pair<String, Boolean>> = listOf(ttp.name to true)
        refreshOtherTTPsView(view, ttp.name, ttpInfo)
        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {
            lifecycleScope.launch {
                if(!ttp.isAllRoles){
                    iPV8CommunicationProtocol.scopePeers()
                }
                else{
                    onDataChangeCallback("Refresh!")
                }
            }
        }
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, iPV8CommunicationProtocol, requireView(), ttp, this)
            }
        }
    }
}
