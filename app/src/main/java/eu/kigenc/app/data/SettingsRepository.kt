package eu.kigenc.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiKey: String = "",
    /** Selbst eingetragener Schlüssel (leer, wenn der Test-Schlüssel genutzt wird). */
    val ownApiKey: String = "",
    val chatModel: String = DEFAULT_CHAT_MODEL,
    val codeModel: String = DEFAULT_CODE_MODEL,
    val autoSpeak: Boolean = false,
    val autoUpdate: Boolean = true,
    val dynamicColor: Boolean = false,
    val imageAgentId: String? = null,
    val cachedModels: List<String> = emptyList(),
    val imageProvider: String = IMAGE_AUTO,
    val bflKey: String = "",
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
    /** true, wenn der gemeinsame Schlüssel der Testversion genutzt wird. */
    val usesTestKey: Boolean get() = ownApiKey.isBlank() && apiKey.isNotBlank()

    companion object {
        // "-latest"-Aliase zeigen immer auf die neueste Modellgeneration von Mistral –
        // so profitiert die App automatisch von neuen Modellen, ohne Update.
        const val DEFAULT_CHAT_MODEL = "mistral-medium-latest"
        const val DEFAULT_CODE_MODEL = "codestral-latest"
        const val IMAGE_AGENT_MODEL = "mistral-medium-latest"

        // Bildgeneratoren
        const val IMAGE_AUTO = "auto"            // Mistral, bei Fehler Pollinations
        const val IMAGE_MISTRAL = "mistral"
        const val IMAGE_POLLINATIONS = "pollinations"
        const val IMAGE_BFL = "bfl"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key_enc")
        val CHAT_MODEL = stringPreferencesKey("chat_model")
        val CODE_MODEL = stringPreferencesKey("code_model")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val IMAGE_AGENT = stringPreferencesKey("image_agent_id")
        val MODELS = stringPreferencesKey("cached_models")
        val IMAGE_PROVIDER = stringPreferencesKey("image_provider")
        val BFL_KEY = stringPreferencesKey("bfl_key_enc")
    }

    val settings: Flow<AppSettings> = combine(context.dataStore.data, eu.kigenc.app.ui.Beta.sharedKey) { p, shared ->
        val own = p[Keys.API_KEY]?.let { Crypto.decrypt(it) }.orEmpty()
        AppSettings(
            apiKey = own.ifBlank { shared.orEmpty() },
            ownApiKey = own,
            chatModel = p[Keys.CHAT_MODEL] ?: AppSettings.DEFAULT_CHAT_MODEL,
            codeModel = p[Keys.CODE_MODEL] ?: AppSettings.DEFAULT_CODE_MODEL,
            autoSpeak = p[Keys.AUTO_SPEAK] ?: false,
            autoUpdate = p[Keys.AUTO_UPDATE] ?: true,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            imageAgentId = p[Keys.IMAGE_AGENT],
            cachedModels = p[Keys.MODELS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            imageProvider = p[Keys.IMAGE_PROVIDER] ?: AppSettings.IMAGE_AUTO,
            bflKey = p[Keys.BFL_KEY]?.let { Crypto.decrypt(it) }.orEmpty(),
        )
    }.flowOn(Dispatchers.IO)

    suspend fun current(): AppSettings = settings.first()

    suspend fun setApiKey(key: String) = context.dataStore.edit {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) it.remove(Keys.API_KEY) else it.set(Keys.API_KEY, Crypto.encrypt(trimmed))
        // Ein neuer Schlüssel kann zu einem anderen Konto gehören → Bild-Agent neu anlegen.
        it.remove(Keys.IMAGE_AGENT)
    }

    suspend fun setChatModel(model: String) = context.dataStore.edit { it[Keys.CHAT_MODEL] = model }
    suspend fun setCodeModel(model: String) = context.dataStore.edit { it[Keys.CODE_MODEL] = model }
    suspend fun setAutoSpeak(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_SPEAK] = v }
    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_UPDATE] = v }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }

    suspend fun setImageAgentId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.IMAGE_AGENT) else it.set(Keys.IMAGE_AGENT, id)
    }

    suspend fun setImageProvider(v: String) = context.dataStore.edit { it[Keys.IMAGE_PROVIDER] = v }

    suspend fun setBflKey(key: String) = context.dataStore.edit {
        val t = key.trim()
        if (t.isEmpty()) it.remove(Keys.BFL_KEY) else it.set(Keys.BFL_KEY, Crypto.encrypt(t))
    }

    suspend fun setCachedModels(models: List<String>) =
        context.dataStore.edit { it[Keys.MODELS] = models.joinToString("\n") }
}
