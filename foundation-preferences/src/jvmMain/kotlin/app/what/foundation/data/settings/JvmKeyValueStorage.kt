package app.what.foundation.data.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JvmKeyValueStorage(
    private val file: File = File(System.getProperty("user.home"), ".what_schedule/preferences.json")
) : KeyValueStorage {
    private val map = ConcurrentHashMap<String, String>()
    private var changeListener: ((String) -> Unit)? = null
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        try {
            file.parentFile?.mkdirs()
            if (file.exists()) {
                val content = file.readText()
                val loaded = json.decodeFromString<Map<String, String>>(content)
                map.putAll(loaded)
            }
        } catch (_: Exception) {}
    }

    private fun persist() {
        try {
            file.parentFile?.mkdirs()
            val text = json.encodeToString(map.toMap())
            file.writeText(text)
        } catch (_: Exception) {}
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return map[key] ?: defaultValue
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            map.remove(key)
        } else {
            map[key] = value
        }
        persist()
        changeListener?.invoke(key)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        changeListener = listener
    }
}
