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
            if (message.startsWith("addr_mess_recv", 0, false)) { // handle message reception (new TTP)
                // update TTP, user List
                Log.i("adr", "received a peer for my address book")
            }
            val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            Log.d("adr", "addressList is null: ${addressList == null}")
            TableHelpers.addAddressesToTable(addressList, addresses, bank, context)
            view.refreshDrawableState()

        }
        val table = view.findViewById<LinearLayout>(R.id.bank_home_deposited_list)
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addDepositedEurosToTable(table, bank)
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

            if (message.startsWith("addr_mess_recv", 0, false)) { // handle message reception (new TTP)
                // update TTP list

                val ttpInfo: MutableList<Pair<String, Boolean>> = mutableListOf(ttp.name to true)
                var commProt = ttp.communicationProtocol as IPV8CommunicationProtocol
                val allNames = commProt.addressBookManager.getAllAddresses()
                    .filter { it.type == Role.TTP || it.type == Role.REG_TTP } // add all TTPS that arent added to TTP list
                    .map { it.name to false }
                    .filter { it !in ttpInfo }

                ttpInfo.addAll(allNames)
                ttpHomeFragment.refreshOtherTTPsView(view, ttp.name,ttpInfo)

                val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
                val addresses = communicationProtocol.addressBookManager.getAllAddresses()
                TableHelpers.addAddressesToTable(addressList, addresses, ttp, context)
                view.refreshDrawableState()

            } else if (message.startsWith("secret_share_recv", 0, false)) { // handle message reception

                ttpHomeFragment.refreshSecretSharesView(view, ttp.connected_Users)

            } else {
                updateUserList(view, ttp)
            }
        }
    }
    fun regttpCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        ttp: REGTTP,
        regttpHomeFragment: REGTTPHomeFragment
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            if (message.startsWith("addr_mess_recv", 0, false)) {
                // update TTP list

                val ttpInfo: MutableList<Pair<String, Boolean>> = mutableListOf(ttp.name to true)
                var commProt = ttp.communicationProtocol as IPV8CommunicationProtocol
                val allNames = commProt.addressBookManager.getAllAddresses()
                    .filter { it.type == Role.TTP }
                    .map { it.name to false }
                    .filter { it !in ttpInfo }

                ttpInfo.addAll(allNames)
                regttpHomeFragment.refreshOtherTTPsView(view, ttp.name, ttpInfo)

                // update users as well, since this is a registrar

                val regUsers = commProt.addressBookManager.getAllAddresses()
                    .filter { it.type == Role.User || it.type == Role.Bank }
                    .map { Triple(it.type.toString(), it.name, it.publicKey.toString()) }

                regttpHomeFragment.refreshRegisteredUsersView(view, regUsers)

                val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
                val addresses = communicationProtocol.addressBookManager.getAllAddresses()
                TableHelpers.addAddressesToTable(addressList, addresses, ttp, context)
                view.refreshDrawableState()

            } else if (message.startsWith("secret_share_recv", 0, false)) {

                regttpHomeFragment.refreshSecretSharesView(view, ttp.connected_Users)

            } else {
                updateUserList(view, ttp)
            }
        }
    }
    private fun updateUserList(
        view: View,
        ttp: TTP
    ) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttp.getRegisteredUsers()
        Log.i("adr_updateuserlist",users.toString())
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    fun userCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        user: User
    ) {
        val k = 2
        val n = 2
        if (message != null) {
            if(message.startsWith("secret_share_recv")){
                if (user.my_shares.size >= k){ // enough shares to recover secret
                    val sortedList = user.my_shares.sortedBy { it.first } // sort alphabetically as they were sent for correct mapping

                    val indexedMap: Map<Int, ByteArray> = sortedList.mapIndexed { index, pair ->
                        (index + 1) to pair.second
                    }.toMap() // map from 1 to k

                    val recovered = user.scheme.join(indexedMap)
                    val recoveredString = String(recovered, Charsets.UTF_8)
                    Log.i("adr_recovery","i should be recovering right about now..." + recoveredString + " " + user.Identification_Value)
                    Toast.makeText(context, "Recovery of secret: ${user.Identification_Value == recoveredString} - $recoveredString", Toast.LENGTH_LONG).show()
                    // TODO: allow transactions (send proof)
                    user.identified = true
                }

            }
            else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            val balanceField = view.findViewById<TextView>(R.id.user_home_balance)
            balanceField.text = user.getBalance().toString()
            val addressList = view.findViewById<LinearLayout>(R.id.participant_address_book)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            Log.d("adr", "addressList is null: ${addressList == null}")
            TableHelpers.addAddressesToTable(addressList, addresses, user, context)
            view.refreshDrawableState()

        }
    }
}
