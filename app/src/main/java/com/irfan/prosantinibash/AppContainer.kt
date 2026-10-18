package com.irfan.prosantinibash

import android.content.Context
import com.irfan.prosantinibash.core.network.SupabaseConfig
import com.irfan.prosantinibash.core.network.SupabaseProvider
import com.irfan.prosantinibash.core.security.PinStore
import com.irfan.prosantinibash.core.settings.PreferencesRepository
import com.irfan.prosantinibash.data.repository.FlatsRepository
import com.irfan.prosantinibash.data.repository.BookRepository
import com.irfan.prosantinibash.data.repository.FilesRepository
import com.irfan.prosantinibash.feature.lock.LockSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

/** Process-owned dependencies. Cloud initialization waits until the app is unlocked. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val preferences = PreferencesRepository(context, scope)
    val session = LockSession()
    val pinStore = PinStore(context)
    private val provider = SupabaseProvider(SupabaseConfig(BuildConfig.SUPABASE_URL,
        BuildConfig.SUPABASE_PUBLISHABLE_KEY))
    val configurationError: String? get() = provider.config.validate()
    private val bookCache = com.irfan.prosantinibash.data.repository.BookCache(context.applicationContext, BuildConfig.SUPABASE_URL)
    private val cachedAtStartup = scope.async(Dispatchers.IO) { bookCache.read() }
    @Volatile private var confirmedBook: com.irfan.prosantinibash.data.model.Book? = null
    suspend fun cachedBook(): com.irfan.prosantinibash.data.model.Book? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        confirmedBook ?: cachedAtStartup.await()?.also { confirmedBook = it }
    }
    val bookRepository by lazy { BookRepository(provider.client) }
    val filesRepository by lazy { FilesRepository(provider.client, context.applicationContext, bookRepository) }
    private val flatsRepository by lazy { FlatsRepository(provider.client) }

    init {
        scope.launch {
            var scheduledReminders: Boolean? = null
            preferences.state.collect { state ->
                if (state.loading || state.error != null) session.lock()
                else {
                    session.setTimeoutMillis(state.preferences.autoLockMinutes * 60_000L)
                    if (scheduledReminders != state.preferences.remindersEnabled) {
                        com.irfan.prosantinibash.core.reminders.Reminders.schedule(context.applicationContext, state.preferences.remindersEnabled)
                        scheduledReminders = state.preferences.remindersEnabled
                    }
                }
            }
        }
    }

    suspend fun loadFlats() = flatsRepository.loadFlats()
    suspend fun loadBook() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        confirmedBook?.let { bookRepository.restoreCache(it) }
        bookRepository.sync().also { book ->
            val changed=confirmedBook !== book
            confirmedBook=book
            if(changed) bookCache.write(book)
        }
    }
    suspend fun commitBook(action: String, data: kotlinx.serialization.json.JsonObject, requestId: String) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            confirmedBook?.let { bookRepository.restoreCache(it) }
            bookRepository.commit(action, data, requestId).also { confirmedBook = it; bookCache.write(it) }
        }
    suspend fun receipt(id: String) = kotlinx.coroutines.withContext(Dispatchers.IO) { bookRepository.receipt(id) }
    suspend fun executeBook(action: String, data: kotlinx.serialization.json.JsonObject, requestId: String) = bookRepository.execute(action,data,requestId)

}
