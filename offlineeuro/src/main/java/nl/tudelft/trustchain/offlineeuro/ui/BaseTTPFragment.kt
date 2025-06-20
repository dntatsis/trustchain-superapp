package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.TTP

abstract class BaseTTPFragment(@LayoutRes layoutId: Int) : OfflineEuroBaseFragment(layoutId) {
    protected lateinit var community: OfflineEuroCommunity
    protected lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    protected lateinit var participant: TTP //

    fun setWelcomeText(view: View, name: String) {
        val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
        welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", name)
    }


    fun refreshOtherTTPsView(view: View, currentName: String, allTTPs: List<Pair<String, Boolean>>) {
        val otherTtpContainer = view.findViewById<LinearLayout>(R.id.ttp_home_other_ttp_list)
        otherTtpContainer?.removeAllViews()

        val otherTtps = allTTPs.filter { it.first != currentName && it.first.startsWith("TTP") }

        if (otherTtps.isEmpty()) {
            otherTtpContainer.addView(TextView(view.context).apply {
                text = "No other TTPs available"
                setPadding(0, 10, 0, 10)
            })
        } else {
            for ((ttpName, isLocal) in otherTtps) {
                otherTtpContainer?.addView(TextView(view.context).apply {
                    text = "$ttpName (${if (isLocal) "Local" else "Remote"})"
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(view.context, android.R.color.black))
                    setPadding(16, 16, 16, 16)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }
    }

     fun refreshSecretSharesView(view: View, connectedUsers: List<Pair<String, ByteArray>>) {
        val userListContainer = view.findViewById<LinearLayout>(R.id.tpp_home_secret_shared_user_list)
        if (userListContainer == null){
            return
        }
        if (userListContainer.childCount > 1) {
            userListContainer.removeViews(1, userListContainer.childCount - 1)
        }

        if (connectedUsers.isEmpty()) {
            userListContainer.addView(TextView(view.context).apply {
                text = "No secret shares available"
                setPadding(0, 10, 0, 10)
            })
            return
        }

        for ((name, secretShare) in connectedUsers) {
            val row = LinearLayout(view.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val nameView = TextView(view.context).apply {
                text = name
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f)
                gravity = Gravity.CENTER
            }

            val shareView = TextView(view.context).apply {
                text = secretShare.toString()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                gravity = Gravity.CENTER
            }

            row.addView(nameView)
            row.addView(shareView)
            userListContainer.addView(row)
        }
    }

}
