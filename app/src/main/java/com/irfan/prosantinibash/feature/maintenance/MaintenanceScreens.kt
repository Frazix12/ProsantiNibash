package com.irfan.prosantinibash.feature.maintenance

import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.expenses.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.UUID
import com.irfan.prosantinibash.data.model.Maintenance
import androidx.compose.ui.res.painterResource

@Composable fun MaintenanceScreen(state: BookUiState, model: BookViewModel, flatId: String? = null, onEdit: (String?) -> Unit, onFiles: (String) -> Unit) {
    val records = state.book.maintenance.filter { !it.voided && (flatId == null || it.flat_id == flatId) }.sortedByDescending { it.maintenance_date }
    BookListPage(state, model::refresh) {
        item { Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(AppText.get(R.string.add_maintenance))
        } }
        item { Text(AppText.get(R.string.paid_costs_are_included_once_in_expenses), style = MaterialTheme.typography.bodyMedium) }
        if (records.isEmpty()) item { EmptyState(AppText.get(R.string.no_maintenance_yet), AppText.get(R.string.record_repairs_work_completed_and_future_reminders)) }
        items(records, key = { it.id }) { MaintenanceRecord(state, model, it, onEdit, onFiles) }
    }
}

@Composable private fun MaintenanceRecord(state: BookUiState, model: BookViewModel, item: Maintenance,
    onEdit: (String?) -> Unit, onFiles: (String) -> Unit) {
    val voidRequest = rememberSaveable(item.id, item.revision) { UUID.randomUUID().toString() }
    SectionCard {
        MoneyRow(item.title, item.amount_paisa, true)
        DetailRow(state.book.flats.firstOrNull { it.id == item.flat_id }?.flatNumber?.let { AppText.get(R.string.flat_value, it) } ?: AppText.get(R.string.building), recordDate(item.maintenance_date))
        item.reminder_date?.let { StatusBadge(AppText.get(R.string.reminder_value, recordDate(it))) }
        item.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        RecordActions(!state.saving && state.pendingRequestId == null, { onEdit(item.id) }, { onFiles("maintenance/${item.id}") }) {
            model.save("maintenance_void", buildJsonObject { put("id", item.id); put("revision", item.revision) }, voidRequest)
        }
    }
}

@Composable fun MaintenanceFormScreen(state: BookUiState, model: BookViewModel, maintenanceId: String? = null, flatId: String? = null, onDone: () -> Unit) {
    val item = state.book.maintenance.firstOrNull { it.id == maintenanceId }
    val editRevision by rememberSaveable(maintenanceId) { mutableStateOf(item?.revision) }
    var title by rememberSaveable(maintenanceId) { mutableStateOf(item?.title ?: "") }
    var date by rememberSaveable(maintenanceId) { mutableStateOf(item?.maintenance_date ?: LocalDate.now(BuildingDates.zone).toString()) }
    var amount by rememberSaveable(maintenanceId) { mutableStateOf(rawAmount(item?.amount_paisa ?: 0)) }
    var scope by rememberSaveable(maintenanceId) { mutableStateOf(state.book.flats.firstOrNull { it.id == (item?.flat_id ?: flatId) }?.flatNumber ?: "Building") }
    var description by rememberSaveable(maintenanceId) { mutableStateOf(item?.description ?: "") }
    var vendor by rememberSaveable(maintenanceId) { mutableStateOf(item?.vendor ?: "") }
    var notes by rememberSaveable(maintenanceId) { mutableStateOf(item?.notes ?: "") }
    var reminder by rememberSaveable(maintenanceId) { mutableStateOf(item?.reminder_date ?: "") }
    val request = rememberSaveable(maintenanceId) { UUID.randomUUID().toString() }
    var attempted by rememberSaveable(maintenanceId) { mutableStateOf(false) }
    val valid = title.isNotBlank() && validDate(date) && (Money.parsePaisa(amount) ?: -1) >= 0 && (reminder.isBlank() || validDate(reminder))
    BookPage(state, model::refresh) {
        if (maintenanceId != null && item == null) { Text(AppText.get(R.string.record_no_longer_available_go_back_and_refresh)); return@BookPage }
        DraftGuard(title != (item?.title ?: "") || amount != rawAmount(item?.amount_paisa ?: 0) || description != (item?.description ?: "") || vendor != (item?.vendor ?: "") || notes != (item?.notes ?: "") || reminder != (item?.reminder_date ?: "") || date != (item?.maintenance_date ?: LocalDate.now(BuildingDates.zone).toString()) || scope != (state.book.flats.firstOrNull { it.id == (item?.flat_id ?: flatId) }?.flatNumber ?: "Building"), state.pendingRequestId != null)
        SectionCard(AppText.get(R.string.work_details)) {
        TextEntry(AppText.get(R.string.title), title) { title = it }; if (attempted && title.isBlank()) Text(AppText.get(R.string.add_a_title_for_this_work), color = MaterialTheme.colorScheme.error)
        DateEntry(AppText.get(R.string.work_date), date) { date = it }
        AmountEntry(AppText.get(R.string.paid_cost_0_for_no_cost), amount) { amount = it }; ChoiceEntry(AppText.get(R.string.scope), flatOptions(state), scope) { scope = it }
        if (attempted && (Money.parsePaisa(amount) ?: -1) < 0) Text(AppText.get(R.string.enter_zero_or_a_positive_cost), color = MaterialTheme.colorScheme.error) }
        SectionCard(AppText.get(R.string.optional_details)) { TextEntry(AppText.get(R.string.description), description) { description = it }; TextEntry(AppText.get(R.string.vendor), vendor) { vendor = it }
        TextEntry(AppText.get(R.string.notes), notes) { notes = it }; DateEntry(AppText.get(R.string.reminder_date), reminder, optional = true) { reminder = it } }
        Text(AppText.get(R.string.a_paid_cost_creates_one_linked_expense_it_is_counted_once))
        if (attempted && (!validDate(date) || (reminder.isNotBlank() && !validDate(reminder)))) Text(AppText.get(R.string.choose_a_valid_work_and_reminder_date), color = MaterialTheme.colorScheme.error)
        SaveButton(state, AppText.get(R.string.save_maintenance)) { attempted = true; if (valid) model.save("maintenance_save", buildJsonObject {
            item?.let { put("id", it.id); put("revision", editRevision!!) }; put("title", title.trim()); put("maintenance_date", date)
            put("amount_paisa", Money.parsePaisa(amount)!!); put("flat_id", com.irfan.prosantinibash.feature.expenses.flatId(state, scope)?.let(::JsonPrimitive) ?: JsonNull)
            put("receipt_path", item?.receipt_path?.let(::JsonPrimitive) ?: JsonNull)
            put("description", description.trim()); put("vendor", vendor.trim()); put("notes", notes.trim())
            put("reminder_date", reminder.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
        }, request, onDone) }
    }
}
