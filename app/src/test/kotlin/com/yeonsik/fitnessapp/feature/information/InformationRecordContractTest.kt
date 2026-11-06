package com.yeonsik.fitnessapp.feature.information

import com.yeonsik.fitnessapp.feature.information.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InformationRecordContractTest {
    private val cases: JsonObject get() {
        val file = listOf(File("../contracts/information-record.v1.cases.json"), File("contracts/information-record.v1.cases.json"))
            .first { it.isFile }
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test fun sharedAcceptedCasesAndOwnerBoundary() {
        for (case in cases.getValue("accepted").jsonArray) {
            val source = case.jsonObject.getValue("record").toString()
            val record = InformationRecordCodec.decode(source)
            assertEquals(record.canonical, InformationRecordCodec.decode(InformationRecordCodec.pretty(record)).canonical)
            if (record.owner == InformationOwner.FITNESS) assertEquals(record.id, InformationRecordCodec.decode(source, InformationOwner.FITNESS).id)
            else assertThrows(Exception::class.java) { InformationRecordCodec.decode(source, InformationOwner.FITNESS) }
        }
    }

    @Test fun sharedRejectedCasesNeverDecode() {
        for (case in cases.getValue("rejected").jsonArray) {
            assertThrows(case.jsonObject.getValue("name").jsonPrimitive.content, Exception::class.java) {
                InformationRecordCodec.decode(case.jsonObject.getValue("record").toString())
            }
        }
    }

    @Test fun exactNestedNumbersAndScalarRepresentationsSurvive() {
        val root = cases.getValue("accepted").jsonArray.first().jsonObject.getValue("record").jsonObject
        val nested = Json.parseToJsonElement("""{"large":12345678901234567890.0010,"negativeZero":-0.00,"exponent":1e3,"false":false,"empty":"","null":null}""")
        val payload = JsonObject(root + ("fields" to JsonArray(root.getValue("fields").jsonArray + buildJsonObject {
            put("key", "raw"); put("value_type", "json"); put("value", nested)
        })))
        val record = InformationRecordCodec.decode(payload.toString(), InformationOwner.FITNESS)
        val roundTrip = InformationRecordCodec.decode(InformationRecordCodec.pretty(record))
        val values = roundTrip.payload.getValue("fields").jsonArray.last().jsonObject.getValue("value").jsonObject
        assertEquals("12345678901234567890.0010", values.getValue("large").jsonPrimitive.content)
        assertEquals("-0.00", values.getValue("negativeZero").jsonPrimitive.content)
        assertEquals("1e3", values.getValue("exponent").jsonPrimitive.content)
        assertEquals(JsonNull, values.getValue("null"))
        assertEquals(record.fingerprint, roundTrip.fingerprint)
    }

    @Test fun keyOrderingIsInsignificantButExactDecimalStringsAreNot() {
        val root = cases.getValue("accepted").jsonArray.first().jsonObject.getValue("record").jsonObject
        val original = InformationRecordCodec.decode(root.toString())
        val reordered = InformationRecordCodec.decode(JsonObject(root.entries.reversed().associate { it.toPair() }).toString())
        assertEquals(original.fingerprint, reordered.fingerprint)
        val changed = InformationRecordCodec.decode(root.toString().replace("12345678901234567890.0010", "12345678901234567890.001"))
        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    @Test fun rawJsonRejectsNonJsonNumberLiterals() {
        val root = cases.getValue("accepted").jsonArray.first().jsonObject.getValue("record").jsonObject
        val source = JsonObject(root + ("fields" to JsonArray(listOf(buildJsonObject {
            put("key", "raw"); put("value_type", "json"); put("value", JsonNull)
        })))).toString()
        for (invalid in listOf("NaN", "Infinity", "undefined", "01", "+1", "1.")) {
            assertThrows(invalid, Exception::class.java) {
                InformationRecordCodec.decode(source.replace("\"value\":null", "\"value\":$invalid"))
            }
        }
    }

    @Test fun hostileNestingIsRejectedBeforeParsingAndQuotedBracesRemainText() {
        assertThrows(IllegalArgumentException::class.java) {
            InformationRecordCodec.decode("[".repeat(50_000) + "]".repeat(50_000))
        }
        val root = cases.getValue("accepted").jsonArray.first().jsonObject.getValue("record").jsonObject
        val text = "[".repeat(100) + "\"\\\"" + "]".repeat(100)
        val payload = JsonObject(root + ("fields" to JsonArray(listOf(buildJsonObject {
            put("key", "quoted"); put("value_type", "text"); put("value", text)
        }))))
        assertEquals(text, InformationRecordCodec.decode(payload.toString()).payload.getValue("fields").jsonArray.single().jsonObject.getValue("value").jsonPrimitive.content)
    }
}
