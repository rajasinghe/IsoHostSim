package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

import java.io.File

/**
 * JSON Schema for host profiles, written next to them so VS Code and IntelliJ offer completion
 * and flag mistakes while QA types. The app's own checks ([HostProfileLoader.validate]) are the
 * authority; this mirrors them for the editor.
 */
object HostProfileSchema {

    private const val FIELD = "^([2-9]|[1-9][0-9]|1[01][0-9]|12[0-8])$"

    val text: String = """
{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Host Simulator profile",
  "type": "object",
  "additionalProperties": false,
  "required": ["name"],
  "properties": {
    "${'$'}schema": { "type": "string" },
    "name": { "type": "string", "minLength": 1, "description": "Gateway name shown in the app." },
    "description": { "type": "string" },
    "gateway": {
      "type": "object", "additionalProperties": false,
      "properties": {
        "type": { "enum": ["SERVER", "PROXY"], "description": "SERVER answers terminals; PROXY forwards them to destination." },
        "transmission": { "enum": ["SYNCHRONOUS", "ASYNCHRONOUS"] },
        "maxConnections": { "type": "integer", "minimum": 1 },
        "timeoutSeconds": { "type": "integer", "minimum": 1, "description": "Wait for a request, and for a PROXY destination's reply." },
        "terminateOnError": { "type": "boolean" }
      }
    },
    "listen": {
      "type": "object", "additionalProperties": false,
      "properties": {
        "connection": { "enum": ["TCP", "RS232"] },
        "address": { "type": "string", "description": "Address to listen on, e.g. the hotspot address 10.42.0.1, or 0.0.0.0." },
        "port": { "type": "integer", "minimum": 1, "maximum": 65535 },
        "serialPort": { "type": "string", "description": "RS232 only, e.g. /dev/ttyUSB0 or COM3." },
        "baudRate": { "type": "integer", "minimum": 1 }
      }
    },
    "destination": {
      "type": "object", "additionalProperties": false, "required": ["address", "port"],
      "description": "Where a PROXY gateway forwards requests.",
      "properties": {
        "address": { "type": "string" },
        "port": { "type": "integer", "minimum": 1, "maximum": 65535 },
        "lengthHeader": { "enum": ["HEX_HL", "HEX_LH", "BCD"] }
      }
    },
    "logging": {
      "type": "object", "additionalProperties": false,
      "properties": {
        "file": { "type": "string", "description": "Empty: ~/.iso8583studio/logs/<name>.log." },
        "maxSizeMB": { "type": "integer", "minimum": 1 },
        "rawData": { "type": "boolean" },
        "textData": { "type": "boolean" },
        "encoding": { "enum": ["ASCII", "UTF8", "ANSI", "Unicode", "BigEndianUnicode", "UTF32", "UTF7"] }
      }
    },
    "framing": {
      "type": "object", "additionalProperties": false,
      "properties": {
        "lengthHeader": { "enum": ["HEX_HL", "HEX_LH", "BCD"], "description": "HEX_HL: 2-byte binary length, used by most terminals." },
        "tpdu": { "enum": ["swap", "fixed", "none"], "description": "swap: answer with the request TPDU, destination and origin swapped." },
        "fixedTpdu": { "type": "string", "pattern": "^[0-9A-Fa-f]{10}${'$'}", "description": "With tpdu \"fixed\", e.g. 6000000096." },
        "asciiMti": { "type": "boolean" }
      }
    },
    "fields": {
      "type": "object",
      "description": "Field formats that differ from the standard template.",
      "propertyNames": { "pattern": "$FIELD" },
      "additionalProperties": {
        "type": "object", "additionalProperties": false, "required": ["type", "max"],
        "properties": {
          "type": { "enum": ["BCD", "AN", "ANS", "BINARY"] },
          "length": { "enum": ["FIXED", "LLVAR", "LLLVAR"] },
          "max": { "type": "integer", "minimum": 1, "maximum": 999, "description": "Digits or characters; bytes for BINARY. LLVAR allows 99 at most." }
        }
      }
    },
    "transactions": {
      "type": "array",
      "items": {
        "type": "object", "additionalProperties": false, "required": ["name", "match"],
        "properties": {
          "name": { "type": "string", "minLength": 1 },
          "match": {
            "type": "object", "additionalProperties": false, "required": ["mti"],
            "properties": {
              "mti": { "type": "string", "pattern": "^[0-9]{4}${'$'}" },
              "processingCode": { "type": "string", "pattern": "^([0-9]{6}|\\*)${'$'}", "description": "Request field 3, or * for any." }
            }
          },
          "respond": {
            "type": "object", "additionalProperties": false,
            "properties": {
              "mti": { "type": "string", "pattern": "^[0-9]{4}${'$'}", "description": "Defaults to the request MTI + 10." },
              "echo": { "type": "array", "items": { "type": "string", "pattern": "$FIELD" }, "description": "Request fields copied back as they are." },
              "set": {
                "type": "object",
                "propertyNames": { "pattern": "$FIELD" },
                "additionalProperties": { "type": "string", "description": "A literal (hex for BINARY) or one of [SV] [TIME] [DATE] [RAND] [AUTO] [GUID] [SIMRC]." }
              }
            }
          }
        }
      }
    },
    "unmatched": {
      "type": "object", "additionalProperties": false,
      "properties": {
        "responseCode": { "type": ["string", "null"], "minLength": 2, "maxLength": 2, "description": "Field 39 for a request no transaction matches; null closes the connection." }
      }
    }
  }
}
""".trimStart()

    fun writeTo(dir: File) {
        dir.mkdirs()
        File(dir, HostProfileLoader.SCHEMA_NAME).writeText(text)
    }
}
