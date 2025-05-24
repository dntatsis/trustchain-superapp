package nl.tudelft.trustchain.offlineeuro.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity

class HomeFragment : OfflineEuroBaseFragment(R.layout.fragment_home) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Home"

        view.findViewById<Button>(R.id.JoinAsTTPButton).setOnClickListener {
            findNavController().navigate(R.id.nav_home_choose_ttp_role )
        }

        view.findViewById<Button>(R.id.JoinAsBankButton).setOnClickListener {
            findNavController().navigate(R.id.nav_home_bankhome)
        }

        view.findViewById<Button>(R.id.JoinAsUserButton).setOnClickListener {
            showAlertDialog()
        }
        view.findViewById<Button>(R.id.JoinAsAllRolesButton).setOnClickListener {
            findNavController().navigate(R.id.nav_home_all_roles_home)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        try {
            val euroTokenCommunity = getIpv8().getOverlay<OfflineEuroCommunity>()
            if (euroTokenCommunity == null) {
                Toast.makeText(requireContext(), "Could not find community", Toast.LENGTH_LONG)
                    .show()
            }
            if (euroTokenCommunity != null) {
                Toast.makeText(requireContext(), "Found community", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: Exception) {
            logger.error { e }
            Toast.makeText(
                requireContext(),
                "Failed to send transactions",
                Toast.LENGTH_LONG
            )
                .show()
        }
        return
    }

    private fun showAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())

        val editText = EditText(requireContext())
        alertDialogBuilder.setView(editText)
        alertDialogBuilder.setTitle("Pick an username")
        alertDialogBuilder.setMessage("")
        // Set positive button
        alertDialogBuilder.setPositiveButton("Join!") { dialog, which ->
            val username = editText.text.toString()
            moveToUserHome(username)
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private fun moveToUserHome(userName: String) {
        val bundle = bundleOf("userName" to userName)
        findNavController().navigate(R.id.nav_home_userhome, bundle)
    }
}
