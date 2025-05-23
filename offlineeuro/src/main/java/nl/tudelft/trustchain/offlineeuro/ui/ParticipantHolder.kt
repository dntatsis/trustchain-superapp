package nl.tudelft.trustchain.offlineeuro.ui

import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User

object ParticipantHolder {
    var ttp: MutableList<TTP>? = null
    var bank: Bank? = null
    var user: User? = null
}
