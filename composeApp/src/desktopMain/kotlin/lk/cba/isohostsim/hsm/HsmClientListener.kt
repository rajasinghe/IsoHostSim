package lk.cba.isohostsim.hsm

import lk.cba.isohostsim.data.HsmClient

interface HsmClientListener {
    fun onDisconnected(hsmClient : HsmClient?)
    fun onSentToSource(data : String?)
    fun onReceivedFormSource(data : String?,hsmClient : HsmClient?)
}