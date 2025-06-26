package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.entity.Bank

class BankHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_bank_home) {
    private lateinit var bank: Bank
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val getIdentityButton = view.findViewById<Button>(R.id.sync_user_button)
        getIdentityButton.setOnClickListener {
            Toast.makeText(requireContext(), "Test", Toast.LENGTH_LONG)
                .show()
            iPV8CommunicationProtocol.scopePeers()
        }
        getIdentityButton.visibility = View.GONE
        if (ParticipantHolder.bank != null) {
            bank = ParticipantHolder.bank!!
            iPV8CommunicationProtocol = bank.communicationProtocol as IPV8CommunicationProtocol
            bank.isAllRoles = true
        } else {
            activity?.title = "Bank"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            val depositedEuroManager = DepositedEuroManager(context, group)
            iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            lifecycleScope.launch {
                bank =
                    Bank(
                        "Bank",
                        group,
                        iPV8CommunicationProtocol,
                        context,
                        depositedEuroManager,
                        onDataChangeCallback = onDataChangeCallBack
                    )
                bank.setUp()
                getIdentityButton.visibility = View.VISIBLE
            }
        }
        getIdentityButton.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {

            lifecycleScope.launch {
                if(!bank.isAllRoles){
                    iPV8CommunicationProtocol.scopePeers()
                }
                else{
                    onDataChangeCallBack("Refresh!")
                }
            }        }
        onDataChangeCallBack(null)
    }

    private val onDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.bankCallback(context, message, iPV8CommunicationProtocol, requireView(), bank)
            }
        }
    }


}
