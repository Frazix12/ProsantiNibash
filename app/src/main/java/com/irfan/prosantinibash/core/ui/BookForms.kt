package com.irfan.prosantinibash.core.ui

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import com.irfan.prosantinibash.feature.book.BookUiState
import java.time.LocalDate
import java.time.YearMonth

private val FormLocked = staticCompositionLocalOf { false }

@Composable fun BookPage(state: BookUiState, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.stale) Text(AppText.get(R.string.previously_confirmed_records_waiting_for_cloud_refresh), style = MaterialTheme.typography.bodySmall)
        if (state.saving || (!state.loaded && state.error == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { EmptyState(AppText.get(R.string.could_not_complete_this_request), appLabel(it), if (state.pendingRequestId == null) AppText.get(R.string.retry) else null, onRefresh) }
        if (state.loaded) CompositionLocalProvider(FormLocked provides (state.pendingRequestId != null)) { content() } else if (state.error == null) Text(AppText.get(R.string.loading_your_building_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun TextEntry(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, enabled = !FormLocked.current, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth())
}
@Composable fun AmountEntry(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(DisplayLocale.digits(value), { onChange(DisplayLocale.asciiDigits(it)) }, enabled = !FormLocked.current, label = { Text(label) }, prefix = { Text("৳") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
@Composable fun DateEntry(label: String, value: String, optional: Boolean = false, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(enabled = !FormLocked.current, modifier = Modifier.weight(1f), onClick = {
            val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
                date.year, date.monthValue - 1, date.dayOfMonth).show()
        }) {
            Icon(painterResource(R.drawable.ic_calendar), null)
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(runCatching { LocalDate.parse(value).format(DisplayLocale.formatter("d MMM yyyy")) }.getOrDefault(AppText.get(R.string.choose_date)))
            }
        }
        if (optional && value.isNotBlank()) IconButton(enabled = !FormLocked.current, onClick = { onChange("") }) {
            Icon(painterResource(R.drawable.ic_delete), AppText.get(R.string.clear_value, label))
        }
    }
}
@Composable fun ChoiceEntry(label: String, options: List<String>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !FormLocked.current, onClick = { expanded = true }) {
            Text("$label: ${appLabel(value.ifBlank { "Choose" })}", Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_next), null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(appLabel(option)) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}
@Composable fun SaveButton(state: BookUiState, label: String, onClick: () -> Unit) {
    Button(onClick, enabled = state.loaded && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(if (state.saving) AppText.get(R.string.saving) else if (state.pendingRequestId != null) AppText.get(R.string.retry_original_save) else label)
    }
}
@Composable fun ConfirmAction(label: String, onConfirm: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text(label) }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("$label?") },
        text = { Text(AppText.get(R.string.this_change_will_be_recorded_in_the_audit_history)) },
        confirmButton = { TextButton(onClick = { show = false; onConfirm() }) { Text(AppText.get(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { show = false }) { Text(AppText.get(R.string.cancel)) } })
}
@Composable fun MonthPicker(month: YearMonth, onChange: (YearMonth) -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !FormLocked.current, onClick = { onChange(month.minusMonths(1)) }) { Icon(painterResource(R.drawable.ic_back), AppText.get(R.string.previous_month)) }
        TextButton(enabled = !FormLocked.current, modifier = Modifier.weight(1f), onClick = { selecting = true }) {
            Text(month.format(DisplayLocale.formatter("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(enabled = !FormLocked.current, onClick = { onChange(month.plusMonths(1)) }) { Icon(painterResource(R.drawable.ic_next), AppText.get(R.string.next_month)) }
    }
    if (selecting) MonthDialog(month, { selecting = false }) { onChange(it); selecting = false }
}
@Composable private fun MonthDialog(month: YearMonth, onDismiss: () -> Unit, onSelect: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(month.year) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get(R.string.choose_month)) }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(painterResource(R.drawable.ic_back), AppText.get(R.string.previous_year)) }
                Text(DisplayLocale.digits(year.toString()), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { year++ }) { Icon(painterResource(R.drawable.ic_next), AppText.get(R.string.next_year)) }
            }
            (1..12).chunked(3).forEach { months -> Row {
                months.forEach { number -> TextButton(modifier = Modifier.weight(1f), onClick = { onSelect(YearMonth.of(year, number)) }) {
                    Text(YearMonth.of(year, number).format(DisplayLocale.formatter("MMM")))
                } }
            } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(AppText.get(R.string.cancel)) } })
}

/** Transaction histories use individually keyed lazy rows rather than one growing scroll column. */
@Composable fun BookListPage(state: BookUiState, onRefresh: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    CompositionLocalProvider(FormLocked provides (state.pendingRequestId != null)) {
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { if (!state.saving) onRefresh() }, modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (state.stale) Text(AppText.get(R.string.previously_confirmed_records_waiting_for_cloud_refresh), style = MaterialTheme.typography.bodySmall)
        if (state.saving || (!state.loaded && state.error == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { EmptyState(AppText.get(R.string.could_not_complete_this_request), appLabel(it), if (state.pendingRequestId == null) AppText.get(R.string.retry) else null, onRefresh) }
        }
        if (state.loaded) content() else if (state.error == null) item { Text(AppText.get(R.string.loading_your_building_records), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
}
}
