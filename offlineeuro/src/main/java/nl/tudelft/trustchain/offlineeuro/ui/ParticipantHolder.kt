package nl.tudelft.trustchain.offlineeuro.ui

import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.REGTTP
import nl.tudelft.trustchain.offlineeuro.entity.User

object ParticipantHolder {
    var regttp: REGTTP? = null
    var ttp: MutableList<TTP>? = null // supports multiple TTPs per emulator
    var bank: Bank? = null
    var user: MutableList<User>? = null //supports multiple users per emulator
}
