package io.github.georg912.plugnap

import org.json.JSONObject

/**
 * Pure settings <-> JSON conversion, deliberately free of any Android
 * dependency (no Context, no SharedPreferences) so it stays plain-JVM
 * testable. [Prefs.exportJson]/[Prefs.importJson] are thin wrappers around
 * this that read/write the actual SharedPreferences.
 */
object PrefsJson {

    /** Keys that are runtime/device state, not a "setting" worth exporting. */
    private val EXCLUDED = setOf("skip_until")

    fun toJson(values: Map<String, *>): String {
        val obj = JSONObject()
        for ((key, value) in values) {
            if (key in EXCLUDED) continue
            when (value) {
                is Boolean, is Int, is Long, is String -> obj.put(key, value)
                else -> {} // unsupported type (e.g. Float, Set<String>) — never used by Prefs
            }
        }
        return obj.toString(2)
    }

    /** Parses [json] into a key->value map ready to be written via a preferences editor. */
    fun fromJson(json: String): Map<String, Any> {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, Any>()
        for (key in obj.keys()) {
            if (key in EXCLUDED) continue
            when (val v = obj.get(key)) {
                is Boolean, is Int, is Long, is String -> result[key] = v
                else -> {}
            }
        }
        return result
    }
}
