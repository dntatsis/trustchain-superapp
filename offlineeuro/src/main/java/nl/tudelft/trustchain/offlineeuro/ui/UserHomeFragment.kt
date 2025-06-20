package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.security.SecureRandom

class UserHomeFragment(val count: Int) : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
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
            user = ParticipantHolder.user!![count]
            communicationProtocol = user.communicationProtocol as IPV8CommunicationProtocol
            val userName: String = user.name
            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName)
            user.isAllRoles = true

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
        updateConnectedInfo(view)
        view.findViewById<Button>(R.id.sync_user_button).setOnClickListener {
            lifecycleScope.launch {
                if(!user.isAllRoles){
                    communicationProtocol.scopePeers()
                }
                else{
                    onUserDataChangeCallBack("Refresh!")
                }
            }
        }
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
                    user,
                    this
                )
            }
        }
    }

    fun updateConnectedInfo(view: View) {
        val listTextView = view.findViewById<TextView>(R.id.print_connected_ttps)

        var connectedTemplate = "List of connected TTPs (_size_): _vals_"
        var updatedText = connectedTemplate
            .replace("_size_", user.connected.size.toString())
            .replace("_vals_", user.connected.joinToString(", "))

        listTextView.text = updatedText

        val userConnectedTextView = view.findViewById<TextView>(R.id.user_home_connection_status)
        connectedTemplate = "You are _connection_status_"
        if(user.identified){
             updatedText = connectedTemplate
                .replace("_connection_status_", "identified!")
            userConnectedTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        }
        else{
            updatedText = connectedTemplate
                .replace("_connection_status_", "unidentified!")
            userConnectedTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))

        }

        userConnectedTextView.text = updatedText

    }

}
