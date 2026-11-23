package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

/** The file a new Host Simulator starts from: a working sale, void, reversal and settlement. */
object HostProfileStarter {

    fun text(name: String): String = """
{
  "${'$'}schema": "${HostProfileLoader.SCHEMA_NAME}",
  "name": "${name.replace("\"", "")}",
  "description": "",

  "gateway": {
    "type": "SERVER",               // SERVER answers terminals; PROXY forwards to "destination"
    "transmission": "SYNCHRONOUS",
    "maxConnections": 100,
    "timeoutSeconds": 30,
    "terminateOnError": false
  },

  "listen": {
    "connection": "TCP",            // TCP, or RS232 with "serialPort" and "baudRate"
    "address": "0.0.0.0",           // or the laptop's hotspot address, e.g. 10.42.0.1
    "port": 8000
  },

  "framing": {
    "lengthHeader": "HEX_HL",       // HEX_HL, HEX_LH or BCD
    "tpdu": "swap"                  // swap, fixed (+ "fixedTpdu": "6000000096") or none
  },

  "logging": {
    "file": "",                     // empty: ~/.pos-testing-platform/logs/<name>.log
    "maxSizeMB": 10,
    "rawData": false,
    "textData": false,
    "encoding": "ASCII"
  },

  // Field formats that differ from the standard template, e.g.
  // "57": { "type": "ANS", "length": "LLLVAR", "max": 999 }
  "fields": {},

  "transactions": [
    {
      "name": "SALE",
      "match": { "mti": "0200", "processingCode": "000000" },
      "respond": {
        "echo": ["3", "4", "11", "24", "41", "42"],
        "set": { "12": "[TIME]", "13": "[DATE]", "37": "[RAND]", "38": "[RAND]", "39": "00" }
      }
    },
    {
      "name": "VOID",
      "match": { "mti": "0200", "processingCode": "020000" },
      "respond": {
        "echo": ["3", "4", "11", "24", "37", "41", "42"],
        "set": { "12": "[TIME]", "13": "[DATE]", "38": "[RAND]", "39": "00" }
      }
    },
    {
      "name": "REVERSAL",
      "match": { "mti": "0400", "processingCode": "*" },
      "respond": {
        "echo": ["3", "4", "11", "24", "41", "42"],
        "set": { "12": "[TIME]", "13": "[DATE]", "39": "00" }
      }
    },
    {
      "name": "SETTLEMENT",
      "match": { "mti": "0500", "processingCode": "*" },
      "respond": {
        "echo": ["3", "11", "24", "41", "42"],
        "set": { "12": "[TIME]", "13": "[DATE]", "37": "[RAND]", "39": "00" }
      }
    }
  ],

  "unmatched": { "responseCode": "96" }   // null: close the connection instead
}
""".trimStart()
}
