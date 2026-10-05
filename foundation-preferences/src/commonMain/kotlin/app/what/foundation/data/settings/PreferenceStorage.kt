package app.what.foundation.data.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import app.what.foundation.ui.useState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty

interface Named {
    val displayName: String
}

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun setOnChangeListener(listener: (key: String) -> Unit)
}

class MemoryKeyValueStorage(
    private val map: MutableMap<String, String?> = mutableMapOf()
) : KeyValueStorage {
    private val lock = Any()
    private var listener: ((String) -> Unit)? = null

    override fun getString(key: String, defaultValue: String?): String? = synchronized(lock) {
        map[key] ?: defaultValue
    }

    override fun putString(key: String, value: String?) {
        val currentListener = synchronized(lock) {
            if (value == null) map.remove(key) else map[key] = value
            listener
        }
        currentListener?.invoke(key)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        synchronized(lock) {
            this.listener = listener
        }
    }
}

interface PreferenceEncryptor {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

abstract class PreferenceStorage(
    protected val storage: KeyValueStorage,
    private val encryptor: PreferenceEncryptor? = null,
) {
    private val preferencesFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)

    init {
        storage.setOnChangeListener { key ->
            preferencesFlow.tryEmit(key)
        }
    }

    fun <T : Any> createValue(
        key: String,
        defaultValue: T?,
        serializer: KSerializer<T>,
        title: String = "",
        description: String? = null,
        icon: ImageVector? = null,
        isEncrypted: Boolean = false,
    ): Value<T> = Value(storage, preferencesFlow, key, defaultValue, serializer, title, description, icon, isEncrypted, encryptor)

    fun boolean(
        key: String,
        defaultValue: Boolean = false,
        title: String = "",
        description: String? = null,
        isEncrypted: Boolean = false
    ): Value<Boolean> = createValue(key, defaultValue, Boolean.serializer(), title, description, null, isEncrypted)

    fun string(
        key: String,
        defaultValue: String = "",
        title: String = "",
        description: String? = null,
        isEncrypted: Boolean = false
    ): Value<String> = createValue(key, defaultValue, String.serializer(), title, description, null, isEncrypted)

    fun int(
        key: String,
        defaultValue: Int = 0,
        title: String = "",
        description: String? = null,
        isEncrypted: Boolean = false
    ): Value<Int> = createValue(key, defaultValue, Int.serializer(), title, description, null, isEncrypted)

    inline fun <reified T : Any> model(
        key: String,
        defaultValue: T? = null,
        title: String = "",
        description: String? = null,
        isEncrypted: Boolean = false
    ): Value<T> = createValue(key, defaultValue, serializer(), title, description, null, isEncrypted)

    class Value<T : Any>(
        private val storage: KeyValueStorage,
        private val preferencesFlow: MutableSharedFlow<String>,
        val key: String,
        private val defaultValue: T?,
        private val serializer: KSerializer<T>,
        val title: String,
        val description: String? = null,
        val icon: ImageVector? = null,
        val isEncrypted: Boolean = false,
        private val encryptor: PreferenceEncryptor? = null,
    ) {
        @Volatile
        private var cachedValue: T? = null
        @Volatile
        private var isCacheLoaded: Boolean = false

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = get()
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = set(value)

        fun get(): T? {
            if (isCacheLoaded) {
                return cachedValue
            }
            val raw = storage.getString(key, null)
            if (raw == null) {
                cachedValue = defaultValue
                isCacheLoaded = true
                return defaultValue
            }
            val jsonString = if (isEncrypted && encryptor != null) {
                try {
                    encryptor.decrypt(raw)
                } catch (e: Exception) {
                    cachedValue = defaultValue
                    isCacheLoaded = true
                    return defaultValue
                }
            } else {
                raw
            }
            val decoded = try {
                Json.decodeFromString(serializer, jsonString)
            } catch (e: Exception) {
                defaultValue
            }
            cachedValue = decoded
            isCacheLoaded = true
            return decoded
        }

        fun set(value: T?) {
            cachedValue = value
            isCacheLoaded = true

            if (value == null) {
                storage.putString(key, null)
            } else {
                val jsonString = Json.encodeToString(serializer, value)
                val stored = if (isEncrypted && encryptor != null) {
                    encryptor.encrypt(jsonString)
                } else {
                    jsonString
                }
                storage.putString(key, stored)
            }
        }

        fun observe(): Flow<T?> = preferencesFlow
            .filter { it == key }
            .map {
                isCacheLoaded = false
                get()
            }
            .onStart { emit(get()) }
            .distinctUntilChanged()

        @Composable
        fun collect(): State<T?> {
            val initial = remember(key) { get() }
            return observe().collectAsState(initial)
        }

        @Composable
        fun <R> collect(block: (T?) -> R): State<R?> {
            val currentBlock by rememberUpdatedState(block)
            val initial = remember(key) { currentBlock(get()) }
            val state = useState<R?>(initial)

            LaunchedEffect(key) {
                observe().collect { state.value = currentBlock(it) }
            }
            return state
        }
    }
}