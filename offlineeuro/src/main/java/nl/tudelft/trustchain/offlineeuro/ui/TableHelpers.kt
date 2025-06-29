package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.shamir.Scheme
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.security.SecureRandom

object TableHelpers {
    fun removeAllButFirstRow(table: LinearLayout) {
        val childrenCount = table.childCount
        val childrenToBeRemoved = childrenCount - 1

        for (i in childrenToBeRemoved downTo 1) {
            val row = table.getChildAt(i)
            table.removeView(row)
        }
    }

    fun addRegisteredUsersToTable(
        table: LinearLayout,
        users: List<RegisteredUser>
    ) {
        val context = table.context
        for (user in users) {
            table.addView(registeredUserToTableRow(user, context))
        }
    }

    private fun registeredUserToTableRow(
        user: RegisteredUser,
        context: Context,
    ): LinearLayout {
        val layout =
            LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

        val idField =
            TextView(context).apply {
                text = user.id.toString()
                layoutParams = layoutParams(0.3f)
                gravity = Gravity.CENTER
            }

        val nameField =
            TextView(context).apply {
                text = user.name
                layoutParams = layoutParams(0.3f)
                gravity = Gravity.CENTER
            }

        val publicKeyField =
            TextView(context).apply {
                text = user.publicKey.toString().take(10) + "..."
                layoutParams = layoutParams(0.4f)
                gravity = Gravity.CENTER
            }

        layout.addView(idField)
        layout.addView(nameField)
        layout.addView(publicKeyField)
        return layout
    }

    fun addDepositedEurosToTable(
        table: LinearLayout,
        bank: Bank
    ) {
        val context = table.context
        for (depositedEuro in bank.depositedEuroLogger) {
            table.addView(depositedEuroToTableRow(depositedEuro, context))
        }
    }

    private fun depositedEuroToTableRow(
        depositedEuro: Pair<String, Boolean>,
        context: Context
    ): LinearLayout {
        val layout =
            LinearLayout(context).apply {
                layoutParams = rowParams()
                orientation = LinearLayout.HORIZONTAL
            }

        val numberField =
            TextView(context).apply {
                text = depositedEuro.first
                layoutParams = layoutParams(0.7f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        val doubleSpendingField =
            TextView(context).apply {
                text = depositedEuro.second.toString()
                layoutParams = layoutParams(0.4f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        layout.addView(numberField)
        layout.addView(doubleSpendingField)
        return layout
    }

    fun addAddressesToTable(
        table: LinearLayout,
        addresses: List<Address>,
        participant: Participant,
        context: Context
    ) {
        removeAllButFirstRow(table)
        for (address in addresses) {
            table.addView(addressToTableRow(address, context, participant))
        }
    }

    private fun addressToTableRow(
        address: Address,
        context: Context,
        participant: Participant
    ): LinearLayout {
        val tableRow = LinearLayout(context)
        tableRow.layoutParams = rowParams()
        tableRow.orientation = LinearLayout.HORIZONTAL

        val styledContext = ContextThemeWrapper(context, R.style.TableCell)
        val roleField =
            TextView(styledContext).apply {
                val weight = if (participant is User) 0.1f else 0.2f
                layoutParams = layoutParams(weight)
                text = address.type.toString()
                gravity = Gravity.CENTER

            }

        val nameField =
            TextView(styledContext).apply {
                val weight = if (participant is User) 0.3f else 0.4f

                layoutParams = layoutParams(weight)
                text = address.name
                gravity = Gravity.CENTER

            }

        val publicKeyField =
            TextView(styledContext).apply {
                val weight = if (participant is User) 0.3f else 0.4f

                layoutParams = layoutParams(weight)
                text = ((address.publicKey.toString()).take(10)) + "..."
                gravity = Gravity.CENTER

            }
        tableRow.addView(roleField)

        tableRow.addView(nameField)
        tableRow.addView(publicKeyField)

        if (participant is User){
            val buttonWrapper = LinearLayout(context).apply {
                layoutParams = layoutParams(0.3f)
                orientation = LinearLayout.HORIZONTAL
            }
            val buttonWeight = 0.5f

            buttonWrapper.orientation = LinearLayout.HORIZONTAL
            buttonWrapper.gravity = Gravity.CENTER_HORIZONTAL
            val mainActionButton = Button(context)
            val secondaryButton = Button(context)
            mainActionButton.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, buttonWeight)
            secondaryButton.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, buttonWeight)


            applyButtonStyling(mainActionButton, context)
            applyButtonStyling(secondaryButton, context)

            buttonWrapper.addView(mainActionButton)
            buttonWrapper.addView(secondaryButton)

            when (address.type) {
                Role.Bank -> {
                    setBankActionButtons(mainActionButton, secondaryButton, address.name, participant, context)
                }
                Role.User -> {
                    setUserActionButtons(mainActionButton, secondaryButton, address.name, participant, context)
                }
                Role.TTP -> {
                    setTTPActionButtons(mainActionButton, secondaryButton, address.name, participant, context)
                }
                Role.REG_TTP -> {
                    setTTPActionButtons(mainActionButton, secondaryButton, address.name, participant, context)
                }

                else -> {}
            }

            tableRow.addView(buttonWrapper)

        }
        return tableRow
    }
    fun setTTPActionButtons(
        mainButton: Button,
        secondaryButton: Button,
        ttpName: String,
        user: User,
        context: Context
    ) {
        mainButton.text = "Connect"
        secondaryButton.text = "Request Share"
        mainButton.setOnClickListener {
            try {
                user.connectToTTP(ttpName)
            } catch (e: Exception) {
                 Toast.makeText(context, "Connect error", Toast.LENGTH_SHORT).show()
            }
        }
        secondaryButton.setOnClickListener {
            try {
                user.recoverShare(ttpName)
            } catch (e: Exception) {
                Toast.makeText(context, "recover error", Toast.LENGTH_SHORT).show()
            }
        }

    }
    fun setBankActionButtons(
        mainButton: Button,
        secondaryButton: Button,
        bankName: String,
        user: User,
        context: Context
    ) {
        mainButton.text = "Withdraw"


        secondaryButton.text = "Deposit"

        if(!user.identified){
            mainButton.background.setTint(context.resources.getColor(R.color.colorPrimarySSI))
            secondaryButton.background.setTint(context.resources.getColor(R.color.colorPrimarySSI))

        }
        else{
            mainButton.setOnClickListener {
                try {
                    val digitalEuro = user.withdrawDigitalEuro(bankName)
                    Toast.makeText(context, "Successfully withdrawn ${digitalEuro.serialNumber}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Withdrawal error", Toast.LENGTH_SHORT).show()
                }
            }

            secondaryButton.setOnClickListener {
                try {
                    val depositResult = user.sendDigitalEuroTo(bankName)

                    Toast.makeText(context, depositResult, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Depositing error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setUserActionButtons(
        mainButton: Button,
        secondaryButton: Button,
        userName: String,
        user: User,
        context: Context
    ) {
        mainButton.text = "Send Euro"
        secondaryButton.text = "Double Spend"

        if(!user.identified){
            mainButton.background.setTint(context.resources.getColor(R.color.colorPrimarySSI))
            secondaryButton.background.setTint(context.resources.getColor(R.color.colorPrimarySSI))

        }
        else{
            mainButton.setOnClickListener {
                try {
                    val result = user.sendDigitalEuroTo(userName)
                } catch (e: Exception) {
                    Toast.makeText(context, "User transfer error", Toast.LENGTH_SHORT).show()
                }
            }

            secondaryButton.setOnClickListener {
                try {
                    val result = user.doubleSpendDigitalEuroTo(userName)
                } catch (e: Exception) {
                    Toast.makeText(context, "User doublespend error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun layoutParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight
        )
    }

    fun rowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    fun applyButtonStyling(
        button: Button,
        context: Context
    ) {
        button.gravity = Gravity.CENTER
        button.setTextColor(context.getColor(R.color.white))
        button.background.setTint(context.resources.getColor(R.color.colorPrimary))
        button.isAllCaps = false
        button.textSize = 8f
        button.setPadding(8, 8, 8, 8)
        button.letterSpacing = 0f
    }
}
