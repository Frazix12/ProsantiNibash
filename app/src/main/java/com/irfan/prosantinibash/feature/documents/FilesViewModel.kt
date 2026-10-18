package com.irfan.prosantinibash.feature.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irfan.prosantinibash.data.model.TenantDocument
import com.irfan.prosantinibash.data.repository.FilesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

data class FileUiState(val uri: String?=null,val busy:Boolean=false,val pending:Boolean=false,
    val progress:Float=0f,val error:String?=null,val cleanupPath:String?=null)

/** Uncertain mutations retain their original inputs, prepared file and request until confirmed. */
class FilesViewModel(private val repository: FilesRepository):ViewModel() {
    private val mutable=MutableStateFlow(FileUiState());val uiState=mutable.asStateFlow()
    private var pendingOperation:(suspend()->String?)?=null
    private var onConfirmed:(()->Unit)?=null
    fun select(uri:Uri) {
        if(!mutable.value.busy && pendingOperation==null && mutable.value.cleanupPath==null) mutable.value=mutable.value.copy(uri=uri.toString(),error=null)
    }
    fun upload(entity:String,id:String,type:String,revision:Long,existing:TenantDocument?,oldPath:String?,onSaved:()->Unit) {
        val uri=mutable.value.uri ?: return
        val request=UUID.randomUUID().toString()
        var file:com.irfan.prosantinibash.data.repository.PickedFile?=null
        begin(onSaved) {
            val prepared=file ?: prepare(uri,type).also { file=it }
            repository.upload(prepared,entity,id,type,request,revision,existing,oldPath) { progress ->
                if (progress == 1f || progress - mutable.value.progress >= .01f) mutable.value=mutable.value.copy(progress=progress)
            }
        }
    }
    private suspend fun prepare(uri:String,type:String):com.irfan.prosantinibash.data.repository.PickedFile = try {
        repository.prepare(Uri.parse(uri),type)
    } catch(error:CancellationException) { throw error }
    catch(error:Exception) {
        pendingOperation=null;onConfirmed=null
        mutable.value=mutable.value.copy(pending=false)
        throw error
    }
    fun delete(document:TenantDocument,onSaved:()->Unit) {
        val request=UUID.randomUUID().toString()
        begin(onSaved) { repository.delete(document,request) }
    }
    private fun begin(onSaved:()->Unit,operation:suspend()->String?) {
        if(mutable.value.busy || pendingOperation!=null || mutable.value.cleanupPath!=null) return
        pendingOperation=operation;onConfirmed=onSaved
        mutable.value=mutable.value.copy(pending=true)
        retry()
    }
    fun retry() {
        val operation=pendingOperation ?: return
        action {
            val cleanup=operation()
            pendingOperation=null
            val callback=onConfirmed;onConfirmed=null
            mutable.value=mutable.value.copy(uri=null,pending=false,cleanupPath=cleanup,
                error=if(cleanup!=null) "Saved to cloud. The previous file could not be removed; retry cleanup." else null)
            callback?.invoke()
        }
    }
    fun retryCleanup() {
        val path=mutable.value.cleanupPath ?: return
        action {
            val remaining=repository.cleanup(path)
            mutable.value=mutable.value.copy(cleanupPath=remaining,
                error=if(remaining!=null) "The previous file could not be removed. Retry cleanup." else null)
        }
    }
    fun open(path:String,mime:String,onOpen:(File)->Unit) { action { onOpen(repository.download(path,mime)) } }
    private fun action(block:suspend()->Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null,progress=0f)
        viewModelScope.launch {
            try { block() } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutable.value=mutable.value.copy(error=if(mutable.value.pending)
                "File operation was not confirmed. Retry the original operation." else "File operation failed. Try again.") }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
}
