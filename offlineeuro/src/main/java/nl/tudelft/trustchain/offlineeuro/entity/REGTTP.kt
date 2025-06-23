package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.db.ConnectedUserManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager

class REGTTP (
    name: String = "REGTTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    connectedUserManager: ConnectedUserManager = ConnectedUserManager(context),
    onDataChangeCallback: ((String?) -> Unit)? = null

) : TTP(name,group,communicationProtocol,context,registeredUserManager,connectedUserManager,onDataChangeCallback)
