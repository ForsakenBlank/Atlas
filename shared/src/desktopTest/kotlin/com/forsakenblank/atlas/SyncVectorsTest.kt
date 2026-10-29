package com.forsakenblank.atlas

import com.forsakenblank.atlas.sync.SyncKey
import com.forsakenblank.atlas.sync.SyncRecord
import com.forsakenblank.atlas.sync.baselineDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

// numbers worked out by Luminescent's python side (core/atlas_sync.py), both must agree to the byte
class SyncVectorsTest {

    @Test
    fun fingerprintsMatchLuminescent() {
        val fields = Json.parseToJsonElement(
            """{"type":"NOTE","name":"Café \"plans\"\nline two","color":-1751987,"pinned":true,"note":{"text":"- [x] milk"},""" +
                """"tags":["b","a"],"updated":5,"created":7,"deletedAt":null,"value":600.0,"parentId":null}""",
        ).jsonObject
        assertEquals("UsgnW-dTQP9pzAq_pIy55l", SyncRecord("item", "x", fields).hash)
    }

    @Test
    fun baselineDigestsMatchLuminescent() {
        assertEquals("yC9N1xC_xrwdlNC5sTlTVRTA66giIAbzQOfAX99Eq0c", baselineDigest(mapOf("item/x" to "abc", "log/y" to "def")))
    }

    @Test
    fun aFrameSealedByLuminescentOpensHere() {
        val sealed = Base64.getDecoder().decode("AAECAwQFBgcICQoLyTr4PNH3G/8Xbfhjb2CfWgy8npo+iC0B9fS9iS+OBPtEkA==")
        assertEquals("""{"action":"hello"}""", SyncKey("123456").open(sealed).decodeToString())
    }
}
