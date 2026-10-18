package com.irfan.prosantinibash.feature.documents

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irfan.prosantinibash.core.export.Exports
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.feature.book.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.irfan.prosantinibash.data.model.TenantDocument
import com.irfan.prosantinibash.feature.expenses.recordDate

@Composable fun FilesScreen(state:BookUiState,bookModel:BookViewModel,files:FilesViewModel,entity:String,id:String) {
    val context=LocalContext.current;val fileState by files.uiState.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf("Other document") }
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    val existing=state.book.tenant_documents.firstOrNull { it.id==replacing }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch(_:SecurityException) { /* Picker still grants access for this running session. */ }; files.select(it) }
    }
    val targetExists = when (entity) { "tenants" -> state.book.tenants.any { it.id == id }; "payments" -> state.book.payments.any { it.id == id }; "expenses" -> state.book.expenses.any { it.id == id }; else -> state.book.maintenance.any { it.id == id } }
    val available = targetExists && !fileState.busy && !fileState.pending && fileState.cleanupPath == null
    DraftGuard(fileState.uri != null || replacing != null, fileState.pending || fileState.cleanupPath != null)
    BookListPage(state,bookModel::refresh) {
        item { SectionCard(if (existing == null) AppText.get(R.string.add_a_file) else AppText.get(R.string.replace_value, existing.file_name)) {
            Text(AppText.get(R.string.pdf_image_or_text_up_to_20_mb), style = MaterialTheme.typography.bodyMedium)
            if(entity=="tenants") { if (available) ChoiceEntry(AppText.get(R.string.type),listOf("Profile photo","NID front","NID back","Agreement","Other document"),type) { type=it } else DetailRow(AppText.get(R.string.type), appLabel(type)) }
            if (!targetExists) Text(AppText.get(R.string.this_record_is_no_longer_available_return_to_its_list_and_refresh), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={ picker.launch(arrayOf("application/pdf","image/jpeg","image/png","image/webp","text/plain")) },enabled=available,modifier=Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_document), null); Spacer(Modifier.width(8.dp)); Text(if(existing==null) AppText.get(R.string.choose_file) else AppText.get(R.string.choose_replacement))
            }
            fileState.uri?.let { StatusBadge(AppText.get(R.string.file_selected))
                Button(onClick={ files.upload(entity,id,type,revision(state,entity,id),existing,existing?.storage_path ?: attachmentPath(state,entity,id)) { replacing=null;bookModel.refresh() } },enabled=available,modifier=Modifier.fillMaxWidth()) { Text(AppText.get(R.string.upload_selected_file)) } }
            if(fileState.busy) { LinearProgressIndicator(progress={fileState.progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth()); Text(AppText.get(R.string.file_operation_in_progress)) }
            fileState.error?.let { Text(appLabel(it), color=MaterialTheme.colorScheme.error) }
            if(fileState.pending) TextButton(onClick=files::retry,enabled=!fileState.busy) { Icon(painterResource(R.drawable.ic_refresh), null); Text(AppText.get(R.string.retry_original_operation)) }
            if(fileState.cleanupPath!=null) TextButton(onClick=files::retryCleanup,enabled=!fileState.busy) { Text(AppText.get(R.string.retry_old_file_cleanup)) }
        } }
        if(entity=="tenants") {
            val docs=state.book.tenant_documents.filter { it.tenant_id==id }
            if(docs.isEmpty()) item { EmptyState(AppText.get(R.string.no_documents), AppText.get(R.string.add_identification_an_agreement_or_a_profile_photo)) }
            docs.forEach { doc -> item(key=doc.id) {
                DocumentRecord(doc, available, onView = { files.open(doc.storage_path,doc.mime_type) { Exports.open(context,it,doc.mime_type) } },
                    onReplace = { replacing=doc.id;type=doc.document_type;picker.launch(arrayOf(doc.mime_type)) },
                    onDelete = { files.delete(doc,bookModel::refresh) })
            } }
        } else item { Attachment(state,files,entity,id,available) }
    }
}

@Composable private fun DocumentRecord(doc: TenantDocument, available: Boolean, onView: () -> Unit,
    onReplace: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_document), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(appLabel(doc.document_type), style = MaterialTheme.typography.titleMedium)
                Text(doc.file_name, style = MaterialTheme.typography.bodyMedium)
                Text(AppText.get(R.string.value_value_kb, recordDate(doc.created_at.take(10)), (doc.size_bytes / 1024).coerceAtLeast(1)), style = MaterialTheme.typography.labelMedium)
            }
        }
        FlowRow {
            TextButton(onClick = onView, enabled = available) { Text(AppText.get(R.string.view_file)) }
            TextButton(onClick = onReplace, enabled = available) { Icon(painterResource(R.drawable.ic_edit), null); Text(AppText.get(R.string.replace)) }
            if (available) ConfirmAction(AppText.get(R.string.delete_document), onDelete)
        }
    }
}
private fun revision(state:BookUiState,entity:String,id:String):Long = when(entity) {
    "payments"->state.book.payments.first { it.id==id }.revision
    "expenses"->state.book.expenses.first { it.id==id }.revision
    "maintenance"->state.book.maintenance.first { it.id==id }.revision
    else->1
}
@Composable private fun Attachment(state:BookUiState,files:FilesViewModel,entity:String,id:String,available:Boolean) {
    val context=LocalContext.current
    val path=attachmentPath(state,entity,id)
    if(path==null) EmptyState(AppText.get(R.string.no_attachment), AppText.get(R.string.upload_a_receipt_or_supporting_document_above)) else TextButton(onClick={ files.open(path,"application/octet-stream") { file ->
        val mime=when(file.extension) { "pdf"->"application/pdf";"jpg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";else->"text/plain" }
        Exports.open(context,file,mime)
    } }, enabled = available) { Icon(painterResource(R.drawable.ic_document), null); Text(AppText.get(R.string.view_attachment)) }
}
private fun attachmentPath(state:BookUiState,entity:String,id:String):String? = when(entity) { "payments"->state.book.payments.firstOrNull { it.id==id }?.attachment_path
        "expenses"->state.book.expenses.firstOrNull { it.id==id }?.receipt_path
        else->state.book.maintenance.firstOrNull { it.id==id }?.receipt_path }
