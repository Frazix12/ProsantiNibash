package com.irfan.prosantinibash.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irfan.prosantinibash.data.model.Audit
import com.irfan.prosantinibash.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuditState(val entries: List<Audit> = emptyList(), val loading: Boolean = false,
    val hasMore: Boolean = true, val error: String? = null, val details: Map<String, Audit> = emptyMap())

/** Cursor pagination keeps historical before/after payloads out of screen and startup reads. */
class AuditViewModel(private val repository: BookRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AuditState())
    val state = mutable.asStateFlow()
    fun load(reset: Boolean = false) {
        if (mutable.value.loading || (!reset && !mutable.value.hasMore)) return
        val cursor = if (reset) null else mutable.value.entries.lastOrNull()
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val page = repository.auditPage(cursor)
                mutable.value = mutable.value.copy(entries = ((if (reset) emptyList() else mutable.value.entries) + page)
                    .distinctBy { it.id }, hasMore = page.size == 50, details = if (reset) emptyMap() else mutable.value.details)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Audit history could not be loaded. Retry.") }
            finally { mutable.value = mutable.value.copy(loading = false) }
        }
    }
    fun detail(id: String) {
        if (id in mutable.value.details) return
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(details = mutable.value.details + (id to repository.auditDetail(id))) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Change details could not be loaded. Retry.") }
        }
    }
}
