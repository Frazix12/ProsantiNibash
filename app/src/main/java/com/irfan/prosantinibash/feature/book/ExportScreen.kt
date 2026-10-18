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

@Composable fun ExportScreen(state:BookUiState,model:BookViewModel,format:String) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) };var retry by remember { mutableIntStateOf(0) }
    val mime=if(format=="csv") "text/csv" else "application/pdf"
    LaunchedEffect(format,retry,state.book) {
        file=null;error=null
        try { file=Exports.report(context,state.book,format);error=null }
        catch(e:CancellationException) { throw e } catch(_:Exception) { error=AppText.get(R.string.export_could_not_be_generated_try_again) }
    }
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if(uri!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out ->
            requireNotNull(file).inputStream().use { it.copyTo(out) } } ?: kotlin.error(AppText.get(R.string.output_unavailable)) } }
            catch(e:CancellationException) { throw e } catch(_:Exception) { error=AppText.get(R.string.export_could_not_be_saved_try_again) } }
    }
    BookPage(state,model::refresh) {
        SectionCard {
            Text(if(format=="csv") AppText.get(R.string.building_ledger) else AppText.get(R.string.financial_report), style=MaterialTheme.typography.titleLarge)
            Text(if(format=="csv") AppText.get(R.string.csv_file_for_spreadsheet_applications) else AppText.get(R.string.pdf_document_ready_to_save_or_share))
        }
        if(file==null && error==null) { LinearProgressIndicator(); Text(AppText.get(R.string.preparing_your_export)) }
        error?.let { Text(appLabel(it), color=MaterialTheme.colorScheme.error);TextButton(onClick={ retry++ }) { Text(AppText.get(R.string.retry)) } }
        file?.let { output ->
            SectionCard(AppText.get(R.string.export_actions)) {
            ActionRow(AppText.get(R.string.save_value, if(format=="csv") "CSV" else "PDF"), { saver.launch(output.name) }, R.drawable.ic_download)
            ActionRow(AppText.get(R.string.share), { try { Exports.open(context,output,mime,true) } catch(_:Exception) { error=AppText.get(R.string.no_share_app_is_available) } }, R.drawable.ic_share)
            }
        }
    }
}
