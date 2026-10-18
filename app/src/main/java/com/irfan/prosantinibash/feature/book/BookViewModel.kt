package com.irfan.prosantinibash.feature.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.data.repository.BookRepository
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class BookUiState(val book:Book=Book(),val loading:Boolean=false,val saving:Boolean=false,
    val stale:Boolean=false,val error:String?=null,val savedSequence:Int=0,val loaded:Boolean=false,val pendingRequestId:String?=null)
private data class PendingSave(val action:String,val data:JsonObject,val id:String)

/** Uncertain requests retain their original payload, including through process restoration. */
class BookViewModel(private val loadBook:suspend()->Book,
    private val execute:suspend(String,JsonObject,String)->Unit,private val saved:SavedStateHandle=SavedStateHandle(),
    private val cachedBook:suspend()->Book?={ null },
    private val commitBook:(suspend(String,JsonObject,String)->Book)?=null,
    private val loadReceipt:(suspend(String)->com.irfan.prosantinibash.data.model.Payment)?=null):ViewModel() {
    constructor(repository:BookRepository):this(repository::load,repository::execute)
    suspend fun receipt(id: String) = loadReceipt?.invoke(id) ?: mutable.value.book.payments.first { it.id == id }
    private var pending=restorePending()
    private val mutable=MutableStateFlow(BookUiState(pendingRequestId=pending?.id))
    val uiState=mutable.asStateFlow()
    private var lastRefresh = 0L
    private var refreshJob: Job? = null
    fun ensureLoaded() {
        if (!mutable.value.loaded || android.os.SystemClock.elapsedRealtime() - lastRefresh > 60_000L) refresh()
    }
    fun refresh() {
        if(mutable.value.loading || mutable.value.saving) return
        mutable.value=mutable.value.copy(loading=true,error=null)
        refreshJob = viewModelScope.launch {
            try {
                if (!mutable.value.loaded) cachedBook()?.let {
                    mutable.value=mutable.value.copy(book=it,loaded=true,stale=true)
                }
                mutable.value=mutable.value.copy(book=loadBook(),loaded=true,stale=false)
                lastRefresh=android.os.SystemClock.elapsedRealtime()
            }
            catch(e:CancellationException) {
                currentCoroutineContext().ensureActive()
                mutable.value=mutable.value.copy(error="Loading was interrupted. Try again.")
            }
            catch(_:Exception) { mutable.value=mutable.value.copy(stale=mutable.value.loaded,error="Records could not be loaded. Check your connection and retry.") }
            finally { mutable.value=mutable.value.copy(loading=false) }
        }
    }
    fun save(action:String,data:JsonObject,requestId:String,onSaved:()->Unit={}) {
        if(mutable.value.saving || !mutable.value.loaded) return
        if(pending!=null && pending?.id!=requestId) {
            mutable.value=mutable.value.copy(error="An earlier save needs confirmation. Return to its form and retry.");return
        }
        val command=pending ?: PendingSave(action,data,requestId).also { keep(it) }
        refreshJob?.cancel()
        mutable.value=mutable.value.copy(saving=true,loading=false,error=null)
        viewModelScope.launch { submit(command,onSaved) }
    }
    private suspend fun submit(command:PendingSave,onSaved:()->Unit) {
        try {
            val confirmed = commitBook?.invoke(command.action,command.data,command.id)
            if (commitBook == null) execute(command.action,command.data,command.id)
            confirmed?.let { mutable.value=mutable.value.copy(book=it,stale=false) }
            keep(null)
            mutable.value=mutable.value.copy(savedSequence=mutable.value.savedSequence+1)
            onSaved()
            try { if (confirmed == null) mutable.value=mutable.value.copy(book=loadBook(),stale=false) }
            catch(e:CancellationException) {
                currentCoroutineContext().ensureActive()
                mutable.value=mutable.value.copy(error="Saved to cloud. Refresh to update the displayed records.")
            }
            catch(_:Exception) { mutable.value=mutable.value.copy(error="Saved to cloud. Refresh to update the displayed records.") }
        } catch(e:CancellationException) {
            currentCoroutineContext().ensureActive()
            mutable.value=mutable.value.copy(error=saveError(e))
        }
        catch(error:Exception) {
            if(error is RestException && error.statusCode in 400..499 && error.statusCode != 408) keep(null)
            mutable.value=mutable.value.copy(error=saveError(error))
        } finally { mutable.value=mutable.value.copy(saving=false) }
    }
    private fun keep(value:PendingSave?) {
        pending=value;saved["pending_action"]=value?.action;saved["pending_data"]=value?.data?.toString();saved["pending_id"]=value?.id
        mutable.value=mutable.value.copy(pendingRequestId=value?.id)
    }
    private fun restorePending():PendingSave? {
        val action=saved.get<String>("pending_action") ?: return null
        val data=saved.get<String>("pending_data") ?: return null
        val id=saved.get<String>("pending_id") ?: return null
        return PendingSave(action,Json.parseToJsonElement(data).jsonObject,id)
    }
    private fun saveError(error:Exception):String {
        if(error !is RestException || error.statusCode >= 500 || error.statusCode == 408) return "Save was not confirmed. Retry sends the original unchanged entry."
        val message=error.message.orEmpty().lowercase()
        val reasons=listOf("changed on another device" to "Record changed on another device. Reload before editing.",
            "already paid" to "This recurring expense was already paid. Reload its schedule.",
            "refund exceeds" to "Refund exceeds the available credit.","exceeds deposit" to "Amount exceeds the deposit balance.",
            "opening balance" to "This date overlaps the opening balance. Correct the opening entry instead.",
            "already occupies" to "This flat already has an active tenant.","already used" to "This request was already confirmed with different values. Reload the record.")
        return reasons.firstOrNull { message.contains(it.first) }?.second ?: "Cloud rejected this entry. Check its values or reload the record."
    }
}
