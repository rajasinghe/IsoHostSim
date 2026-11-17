package lk.cba.isohostsim.hsm.payshield10k

import lk.cba.isohostsim.hsm.payshield10k.data.AuditEntry
import lk.cba.isohostsim.hsm.payshield10k.data.AuditLog

interface HsmLogsListener {
    fun onAuditLog(auditLog: AuditEntry)
    fun log(auditLog: String)
    fun onFormattedRequest(log: String)
    fun onFormattedResponse(log: String)
}