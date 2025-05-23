package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.IDTTP
import nl.tudelft.trustchain.offlineeuro.entity.TTP

class TTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: MutableList<TTP>
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.ttp != null) {
            ttp = ParticipantHolder.ttp!!
        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            var IDTTP_instance : TTP = IDTTP("TTP", group, iPV8CommunicationProtocol, context, onDataChangeCallback = onDataChangeCallback)
            ttp.add(IDTTP_instance)
        }
        onDataChangeCallback(null)
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp[0])
            }
        }
    }
}
