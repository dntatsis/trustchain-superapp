package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.ui.TableHelpers.layoutParams

object CallbackLibrary {
    fun bankCallback(
        context: Context,
        message: String?,
        communicationProtocol: IPV8CommunicationProtocol,
        view: View,
        bank: Bank
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
                .filter { it.name != bank.name }

            if (addressList != null) {
                TableHelpers.addAddressesToTable(addressList, addresses, bank, context)
                view.refreshDrawableState()
            }
        }

        val table = view.findViewById<LinearLayout>(R.id.bank_home_deposited_list)
        if (table != null) {
            TableHelpers.removeAllButFirstRow(table)
            TableHelpers.addDepositedEurosToTable(table, bank)
        }

        fun updateFraudInfo(view: View) {
            val listTextView = view.findViewById<TextView>(R.id.print_fraud_users)
            val connectedTemplate = "List of fraud Users: [_vals_]"
            val updatedListText = connectedTemplate
                .replace("_vals_", bank.fraudUsers.joinToString(", "))

            listTextView?.text = updatedListText
        }
        updateFraudInfo(view)
    }
    fun ttpCallback(
        context: Context,
        message: String?,
        communicationProtocol: IPV8CommunicationProtocol,
        view: View,
        ttp: TTP,
        ttpHomeFragment: TTPHomeFragment
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            val ttpInfo: MutableList<Pair<String, Boolean>> = mutableListOf(ttp.name to true)
            val commProt = ttp.communicationProtocol as? IPV8CommunicationProtocol
            if (commProt != null) {
                val allNames = commProt.addressBookManager.getAllAddresses()
                    .filter { it.type == Role.TTP || it.type == Role.REG_TTP }
                    .map { it.name to false }
                    .filter { it !in ttpInfo }

                ttpInfo.addAll(allNames)
                ttpHomeFragment.refreshOtherTTPsView(view, ttp.name, ttpInfo)
            }

            val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
                .filter { it.name != ttp.name }

            if (addressList != null) {
                TableHelpers.addAddressesToTable(addressList, addresses, ttp, context)
                view.refreshDrawableState()
            }

            ttpHomeFragment.refreshSecretSharesView(view, ttp.connected_Users)
        }

        updateUserList(view, ttp)
    }
    fun regttpCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        ttp: REGTTP,
        regttpHomeFragment: REGTTPHomeFragment
    ) {
        Log.i("adr_invoke", "invoked from inside $message")

        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        updateUserList(view, ttp)
    }
    private fun updateUserList(
        view: View,
        ttp: TTP
    ) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    fun userCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        user: User,
        userFragment: UserHomeFragment
    ) {
        if (message != null) {
            if (message.startsWith("secret_share_recv")) {
                Log.i("adr", "callback with secret share: $message,\n ${user.myShares}")
            }

            if ((user.myShares.count { it.second.isNotEmpty() } >= User.minimum_shares) && !user.identified) {
                val indexedMap: Map<Int, ByteArray> = user.myShares
                    .mapIndexed { newIndex, pair -> (newIndex + 1) to pair.second }
                    .filter { it.second.isNotEmpty() }
                    .toMap()

                val recovered = user.scheme.join(indexedMap)
                val recoveredString = String(recovered, Charsets.UTF_8)
                Log.i("adr_recovery", "i should be recovering right about now... $indexedMap Verification: ${recoveredString == user.Identification_Value}")
                Toast.makeText(context, "Recovery of secret: ${user.Identification_Value == recoveredString} - $recoveredString", Toast.LENGTH_LONG).show()
                user.identified = (recoveredString == user.Identification_Value)
            }

            val balanceField = view.findViewById<TextView>(R.id.user_home_balance)
            balanceField?.text = user.getBalance().toString()

            val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
            if (addressList != null) {
                val addresses = communicationProtocol.addressBookManager.getAllAddresses()
                    .filter { it.name != user.name }
                TableHelpers.addAddressesToTable(addressList, addresses, user, context)
            }

            userFragment.updateConnectedInfo(view)
            view.refreshDrawableState()
        }
    }
}
