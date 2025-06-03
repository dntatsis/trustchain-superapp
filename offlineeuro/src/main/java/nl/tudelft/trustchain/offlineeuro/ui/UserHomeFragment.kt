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
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.security.SecureRandom

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity
    private lateinit var communicationProtocol: IPV8CommunicationProtocol
    private lateinit var connectedTemplate: String
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.user != null) {
            user = ParticipantHolder.user!!
            communicationProtocol = user.communicationProtocol as IPV8CommunicationProtocol
            val userName: String = user.name
            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName)

        } else {
            activity?.title = "User"

            val userName: String? = arguments?.getString("userName")

            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName!!)
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            val syncbutton = view.findViewById<Button>(R.id.sync_user_button)
            syncbutton.visibility = View.GONE
            var connectedSuccessfully = true;
            // Make connection and waiting for group description and crs not block the main thread
            lifecycleScope.launch {
                try {
                    user = User(
                        userName,
                        group,
                        context,
                        null,
                        communicationProtocol,
                        onDataChangeCallback = null,
                        Identification_Value = "my_secret"
                    )
                    user.setup()
                    //communicationProtocol.scopePeers()
                    syncbutton.visibility = View.VISIBLE
                    user.onDataChangeCallback = onUserDataChangeCallBack
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

            if (!connectedSuccessfully) return
        }

        val listTextView = view.findViewById<TextView>(R.id.print_connected_ttps)

        fun updateConnectedInfo(view: View) { // update the info of the connected TTPs, (number and entries)

            val updatedText = connectedTemplate
                .replace("_size_", user.connected.size.toString())
                .replace("_vals_", user.connected.toString())
            listTextView.text = updatedText
        }

        connectedTemplate = listTextView.text.toString()
        updateConnectedInfo(view)


        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {
            lifecycleScope.launch {
                communicationProtocol.scopePeers()
            }
        }


        view.findViewById<Button>(R.id.request_shares).setOnClickListener { // request your share from all connected TTPs
            val connectedNames = user.connected.map { it }
            for (nameConnected in connectedNames) {
                communicationProtocol.requestShare(user.name, nameConnected)
            }
        }

        view.findViewById<Button>(R.id.user_connect_ttps).setOnClickListener { // Connect up to n TTPs, and send your shares
            val n = 2 // TODO: make global
            val k = 2
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            val connectedNames = user.connected.map { it }
            for (address in addresses) {
                if ((address.type == Role.REG_TTP || address.type == Role.TTP) && address.name !in connectedNames){
                    // add element to connected TTP list
                    user.connected.add(address.name)

                    if (user.connected.size >= n){
                        // if n connections, secret share
                        user.scheme = Scheme(SecureRandom(), n, k)
                        val parts = user.scheme.split(user.Identification_Value.toByteArray(Charsets.UTF_8))
                        val partialParts = parts.entries.take(n).associate { it.toPair() }
                        val partsList = partialParts.values.toList()
                        user.connected.sort() // sort alphabetically for recovery

                        for (i in user.connected.indices) {
                            communicationProtocol.connect(user.name, partsList[i]!!, user.connected[i])
                        }
                        break
                    }

                }
            }
            updateConnectedInfo(view)
        }

        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {
            communicationProtocol.scopePeers()
        }

        // Set up Shamir algo demo
        /* view.findViewById<Button>(R.id.shamir_button).setOnClickListener {
            val secret = "this_is_my_private_id".toByteArray(Charsets.UTF_8)
            val scheme = Scheme(SecureRandom(), 3, 2)
            val parts = scheme.split(secret)
            val partialParts = parts.entries.take(2).associate { it.toPair() }
            val recovered = scheme.join(partialParts)
            val recoveredString = String(recovered, Charsets.UTF_8)
            Toast.makeText(context, "Recovered: $recoveredString", Toast.LENGTH_LONG).show()
        } */

        val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        TableHelpers.addAddressesToTable(addressList, addresses, user, requireContext())
        onUserDataChangeCallBack(null)
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            val context = requireContext()
            if (this::user.isInitialized) {
                CallbackLibrary.userCallback(
                    context,
                    message,
                    requireView(),
                    communicationProtocol,
                    user
                )
            }
        }
    }
}
