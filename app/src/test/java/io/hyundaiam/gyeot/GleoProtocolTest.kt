package io.hyundaiam.gyeot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GleoProtocolTest {
    @Test fun acceptsBothDocumentedObjectAndDoubleEncodedActionParameters() {
        val raw = "{\"period\":\"latest\"}"
        assertEquals("latest", GleoProtocol.parameters(raw).getString("period"))
        assertEquals("latest", GleoProtocol.parameters(JSONObject.quote(raw)).getString("period"))
    }
    @Test fun callbackDataIsAJsonArrayEncodedAsAString() {
        val response = GleoProtocol.callback("GET_DRIVING_CONTEXT", "success", "The driving context is available.", JSONArray().put(JSONObject().put("source", "DEMO")))
        assertEquals("voice", response.getString("mode"))
        assertTrue(response.get("data") is String)
        assertEquals("DEMO", JSONArray(response.getString("data")).getJSONObject(0).getString("source"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonObjectParameters() { GleoProtocol.parameters("[]") }
    @Test fun responsesCannotUseAnArbitraryOrSelfTarget() {
        assertFalse(GleoProtocol.validPackage(""))
        assertFalse(GleoProtocol.validPackage("io.hyundaiam.gyeot"))
        assertFalse(GleoProtocol.validPackage("bad package"))
        assertTrue(GleoProtocol.validPackage("ai.example.gleo"))
    }
}
