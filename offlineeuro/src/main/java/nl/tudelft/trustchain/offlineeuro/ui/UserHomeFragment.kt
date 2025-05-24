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
            Log.i("adr_reg",userName)

        } else {
            activity?.title = "User"

            val userName: String? = arguments?.getString("userName")
            Log.i("adr_reg_null",userName!!)

            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName!!)
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            try {
                user = User(userName, group, context, null, communicationProtocol, onDataChangeCallback = onUserDataChangeCallBack)
                Log.i("adr_user", "user init successful, scoping peers...: $user")
                communicationProtocol.scopePeers()

            } catch (e: Throwable) {
                Log.e("adr_user", "User creation failed", e)
                Toast.makeText(context, "User creation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Log.i("adr_user",communicationProtocol.addressBookManager.getAllAddresses().toString())
        }

        val listTextView = view.findViewById<TextView>(R.id.print_connected_ttps)

        fun updateConnectedInfo(view: View) {
            Log.i("adr_found", "im in")

            val updatedText = connectedTemplate
                .replace("_size_", user.connected.size.toString())
                .replace("_vals_", user.connected.toString())
            listTextView.text = updatedText
        }

        connectedTemplate = listTextView.text.toString()
        updateConnectedInfo(view)


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
                if ((address.type == Role.REG_TTP || address.type == Role.TTP) && address.name !in connectedNames){
                    // add element
                    Log.i("adr_found", address.toString())
                    user.connected.add(address.name)
                    Log.i("adr_found", user.connected.toString())

                    if (user.connected.size >= n){
                        // if n connections, secret share
                        val scheme = Scheme(SecureRandom(), n, k)
                        val parts = scheme.split(user.Identification_Value.toByteArray(Charsets.UTF_8))
                        val partialParts = parts.entries.take(n).associate { it.toPair() }
                        val partsList = partialParts.values.toList()
                        Log.i("adr1", user.connected.toString())
                        Log.i("adr2", partsList.toString())

                        for (i in user.connected.indices) {
                            Log.i("adr2", partsList[i].toString())
                            Log.i("adr3", user.connected[i])
                            Log.i("adr4", user.name)

                            communicationProtocol.connect(user.name, partsList[i]!!, user.connected[i])
                        }

                        Log.i("adr", partialParts.toString())
                        break
                    }

                }
            }
            updateConnectedInfo(view)
        }

        view.findViewById<Button>(R.id.user_home_sync_addresses).setOnClickListener {
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
