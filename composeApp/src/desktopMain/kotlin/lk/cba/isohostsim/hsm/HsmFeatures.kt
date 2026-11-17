package lk.cba.isohostsim.hsm

import lk.cba.isohostsim.hsm.payshield10k.HsmSlotManager

interface HsmFeatures {
    fun getSlotManager(): HsmSlotManager
}