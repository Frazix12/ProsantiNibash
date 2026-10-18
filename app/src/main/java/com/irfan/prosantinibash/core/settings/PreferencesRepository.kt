package com.irfan.prosantinibash.core.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.settingsStore by preferencesDataStore("settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoLockMinutes: Int = 1,
    val biometricsEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
    val language: String = "en",
)
data class PreferencesState(
    val preferences: AppPreferences = AppPreferences(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Stores appearance and lock preferences, never PINs or unlock state. */
class PreferencesRepository(private val context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore
    private val mutableState = MutableStateFlow(PreferencesState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")
    private val dynamic = booleanPreferencesKey("dynamic_color")
    private val timeout = intPreferencesKey("auto_lock_minutes")
    private val reminders = booleanPreferencesKey("reminders")
    private val biometric = booleanPreferencesKey("biometrics")
    init { com.irfan.prosantinibash.core.i18n.AppText.configure(context, "en"); reload() }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            store.data.catch { error ->
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(loading = false,
                    error = "Settings could not be read. Try again.")
            }.collect { values ->
                val tag = values[language]?.takeIf { it in setOf("en", "bn") } ?: "en"
                com.irfan.prosantinibash.core.i18n.AppText.configure(context, tag)
                val mode = ThemeMode.entries.find { it.name == values[theme] } ?: ThemeMode.SYSTEM
                val minutes = values[timeout]?.takeIf { it in setOf(0, 1, 5, 15) } ?: 1
                mutableState.value = PreferencesState(AppPreferences(mode,
                    values[dynamic] ?: false, minutes, values[biometric] ?: false, values[reminders] ?: false, tag), false)
            }
        }
    }
    suspend fun setLanguage(value: String) {
        require(value in setOf("en", "bn"))
        store.edit { it[language] = value }
    }
    suspend fun setThemeMode(value: ThemeMode) { store.edit { it[theme] = value.name } }
    suspend fun setDynamicColor(value: Boolean) { store.edit { it[dynamic] = value } }
    suspend fun setAutoLockMinutes(value: Int) {
        require(value in setOf(0, 1, 5, 15))
        store.edit { it[timeout] = value }
    }
    suspend fun setBiometricsEnabled(value: Boolean) { store.edit { it[biometric] = value } }
    suspend fun setRemindersEnabled(value: Boolean) { store.edit { it[reminders] = value } }

}
