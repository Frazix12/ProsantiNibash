package com.irfan.prosantinibash.feature.book

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.irfan.prosantinibash.core.export.Exports
import com.irfan.prosantinibash.core.ui.*
import kotlinx.coroutines.*
import java.io.File
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable fun ReceiptScreen(state:BookUiState,model:BookViewModel,paymentId:String) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) };var error by remember { mutableStateOf<String?>(null) }
    var issued by remember(paymentId) { mutableStateOf<com.irfan.prosantinibash.data.model.Payment?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val payment=state.book.payments.firstOrNull { it.id==paymentId }
    LaunchedEffect(payment?.id,payment?.revision,payment?.voided,generation) {
        file=null;error=null;issued=null
        if(payment==null) return@LaunchedEffect
        try {
            val original=model.receipt(paymentId)
            issued=original
            file=Exports.receipt(context,state.book.copy(payments=listOf(original)),paymentId)
        }
        catch(e:CancellationException) { throw e } catch(_:Exception) { error=AppText.get(R.string.receipt_could_not_be_generated_try_again) }
    }
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> requireNotNull(file).inputStream().use { it.copyTo(out) } } ?: kotlin.error(AppText.get(R.string.output_unavailable)) } }
            catch(e:CancellationException) { throw e } catch(_:Exception) { error=AppText.get(R.string.pdf_could_not_be_saved_try_again) } }
    }
    BookPage(state,model::refresh) {
        payment?.let { SectionCard {
            Text(it.receipt_number,style=MaterialTheme.typography.titleMedium)
            if (issued != null) MoneyRow(AppText.get(R.string.issued_receipt_amount), issued?.receipt_snapshot?.get("amount_paisa")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: issued?.amount_paisa ?: 0L, true)
            Text(AppText.get(R.string.receipt_uses_the_original_issuance_details), style=MaterialTheme.typography.bodySmall)
            if(it.voided) StatusBadge(AppText.get(R.string.voided)) else if(it.revision > 1) StatusBadge(AppText.get(R.string.corrected_since_issue))
        } }
        if(payment==null) EmptyState(AppText.get(R.string.receipt_unavailable), AppText.get(R.string.refresh_to_check_this_payment_again))
        else if(file==null && error==null) { LinearProgressIndicator(); Text(AppText.get(R.string.preparing_your_pdf)) }
        error?.let { Text(appLabel(it), color=MaterialTheme.colorScheme.error)
            TextButton(onClick={ generation++ }) { Text(AppText.get(R.string.retry_receipt)) }
        }
        file?.let { pdf ->
            SectionCard(AppText.get(R.string.receipt_actions)) {
            ActionRow(AppText.get(R.string.view_receipt), { try { Exports.open(context,pdf,"application/pdf") } catch(_:Exception) { error=AppText.get(R.string.no_pdf_viewer_is_available) } }, R.drawable.ic_receipt)
            ActionRow(AppText.get(R.string.share_receipt), { try { Exports.open(context,pdf,"application/pdf",true) } catch(_:Exception) { error=AppText.get(R.string.receipt_could_not_be_shared) } }, R.drawable.ic_share)
            ActionRow(AppText.get(R.string.save_pdf), { saver.launch(pdf.name) }, R.drawable.ic_download)
            ActionRow(AppText.get(R.string.print), { try { Exports.print(context,pdf) } catch(_:Exception) { error=AppText.get(R.string.print_service_is_unavailable) } }, R.drawable.ic_print)
            }
        }
    }
}
