package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the settings export/import round-trip (no Context needed). */
class PrefsJsonTest {

    private val sample: Map<String, Any> = mapOf(
        "enabled" to true,
        "window_start" to 1260,
        "schedule_mode" to "PER_DAY",
        "day_start_3" to 1260,
        "skip_until" to 1234567890123L, // must never survive export/import
    )

    @Test
    fun `round-trips booleans, ints and strings`() {
        val json = PrefsJson.toJson(sample)
        val restored = PrefsJson.fromJson(json)
        assertEquals(true, restored["enabled"])
        assertEquals(1260, restored["window_start"])
        assertEquals("PER_DAY", restored["schedule_mode"])
        assertEquals(1260, restored["day_start_3"])
    }

    @Test
    fun `excludes skip_until on export`() =
        assertFalse(PrefsJson.toJson(sample).contains("skip_until"))

    @Test
    fun `excludes skip_until on import even if present in the JSON`() {
        val json = """{"enabled": true, "skip_until": 999}"""
        assertFalse(PrefsJson.fromJson(json).containsKey("skip_until"))
    }

    @Test
    fun `unknown keys survive - forward compatible with future settings`() {
        val json = """{"some_future_setting": true}"""
        assertTrue(PrefsJson.fromJson(json).containsKey("some_future_setting"))
    }

    @Test
    fun `malformed JSON throws rather than silently importing nothing`() {
        var threw = false
        try {
            PrefsJson.fromJson("not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `empty map exports to an empty JSON object`() =
        assertEquals(emptyMap<String, Any>(), PrefsJson.fromJson(PrefsJson.toJson(emptyMap<String, Any>())))
}
