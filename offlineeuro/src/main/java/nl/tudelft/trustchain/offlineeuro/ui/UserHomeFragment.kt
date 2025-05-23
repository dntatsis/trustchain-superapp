package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

            val listTextView = view.findViewById<TextView>(R.id.print_connected_ttps)
            listTextView.text = listTextView.text.toString().replace("_size_", user.connected.size.toString())
            listTextView.text = listTextView.text.toString().replace("_vals_", user.connected.toString())

        } else {
            activity?.title = "User"
            val userName: String? = arguments?.getString("userName")
            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName!!)
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            try {
                user = User(userName, group, context, null, communicationProtocol, onDataChangeCallback = onUserDataChangeCallBack)
                communicationProtocol.scopePeers()
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.user_home_reset_button).setOnClickListener {
            communicationProtocol.addressBookManager.clear()
            user.reset()
            val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            TableHelpers.addAddressesToTable(addressList, addresses, user, requireContext())
        }

        view.findViewById<Button>(R.id.user_connect_ttps).setOnClickListener {
            val n = 3
            val k = 2
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            Log.i("adr", addresses.toString())
            val connectedNames = user.connected.map { it }
            for (address in addresses) {
                if ((address.type == Role.ID_TTP || address.type == Role.TTP) && address.name !in connectedNames)
                    // add element
                    print("hi")
                    user.connected.add(address.name)
                    if (user.connected.size >= n)
                        // if n connections, secret share
                        val scheme = Scheme(SecureRandom(), n, k)
                        val parts = scheme.split(user.Identification_Value)
                        val partialParts = parts.entries.take(2).associate { it.toPair() }
                        Log.i("adr", partialParts.toString())
                        print("Hi!")
                        // communicationProtocol.connect(user.name,partialParts[i])
                        break
            }

        }
        view.findViewById<Button>(R.id.user_home_sync_addresses).setOnClickListener {
            communicationProtocol.scopePeers()
        }

        // Set up Shamir algo demo
        view.findViewById<Button>(R.id.shamir_button).setOnClickListener {
            val secret = "this_is_my_private_id".toByteArray(Charsets.UTF_8)
            val scheme = Scheme(SecureRandom(), 3, 2)
            val parts = scheme.split(secret)
            val partialParts = parts.entries.take(2).associate { it.toPair() }
            val recovered = scheme.join(partialParts)
            val recoveredString = String(recovered, Charsets.UTF_8)
            Toast.makeText(context, "Recovered: $recoveredString", Toast.LENGTH_LONG).show()
        }

        val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
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
