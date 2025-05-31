package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
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
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import android.widget.Button
import androidx.navigation.fragment.findNavController

class ChooseTTPRoleFragment : OfflineEuroBaseFragment(R.layout.fragment_choose_ttp_role) {

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?
        ) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<Button>(R.id.button_add_secretsharer).setOnClickListener {
                findNavController().navigate(R.id.nav_choose_ttp_ttphome)

            }

            view.findViewById<Button>(R.id.button_add_registrar).setOnClickListener {
                findNavController().navigate(R.id.nav_choose_ttp_ttpreg)

            }
        }




}
