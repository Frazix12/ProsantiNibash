package com.irfan.prosantinibash.feature.expenses

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
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
import com.irfan.prosantinibash.data.model.RecurringExpense
import com.irfan.prosantinibash.feature.book.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.UUID
import androidx.compose.ui.res.painterResource

@Composable fun RecurringScreen(state: BookUiState, model: BookViewModel) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var formVisible by rememberSaveable { mutableStateOf(false) }
    var paying by rememberSaveable { mutableStateOf<String?>(null) }
    val entries=remember(state.book.recurring_expenses) { state.book.recurring_expenses.sortedBy { it.next_date } }
    val head: @Composable () -> Unit = {
        Text(AppText.get(R.string.schedules_create_reminders_record_an_expense_only_after_payment))
        Button(onClick = { editing = null; formVisible = true }, enabled = !state.saving && state.pendingRequestId == null && !formVisible && paying == null) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(AppText.get(R.string.add_schedule)) }
        if (formVisible) RecurringForm(state, model, editing) { formVisible = false }
        entries.firstOrNull { it.id == paying }?.let { RecurringPayment(state, model, it) { paying = null } }
        if (entries.isEmpty()) EmptyState(AppText.get(R.string.no_schedules), AppText.get(R.string.set_up_a_reminder_for_regular_building_expenses))
    }
    val row: @Composable (RecurringExpense) -> Unit = { item ->
        RecurringRow(item,!state.saving && state.pendingRequestId == null && !formVisible && paying == null,
            { editing=item.id;formVisible=true },{ paying=item.id })
    }
    // Keep draft guards composed while editing; browsing schedules uses lazy rows.
    if (formVisible || paying != null) BookPage(state,model::refresh) { head();entries.forEach { row(it) } }
    else BookListPage(state,model::refresh) { item { head() };items(entries,key={ it.id }) { row(it) } }
}

@Composable private fun RecurringRow(item:RecurringExpense,enabled:Boolean,onEdit:()->Unit,onPay:()->Unit) {
    SectionCard {
        MoneyRow(item.description,item.amount_paisa,true)
        DetailRow(appLabel(item.frequency.replaceFirstChar(Char::uppercaseChar)),AppText.get(R.string.next_value, recordDate(item.next_date)))
        StatusBadge(if(item.active) AppText.get(R.string.active_schedule) else AppText.get(R.string.paused))
        Row {
            TextButton(onClick=onEdit,enabled=enabled) { Icon(painterResource(R.drawable.ic_edit),null);Text(AppText.get(R.string.edit)) }
            if(item.active) TextButton(onClick=onPay,enabled=enabled) { Icon(painterResource(R.drawable.ic_check),null);Text(AppText.get(R.string.record_payment)) }
        }
    }
}

@Composable private fun RecurringForm(state: BookUiState, model: BookViewModel, id: String?, onDone: () -> Unit) {
    val item = state.book.recurring_expenses.firstOrNull { it.id == id }
    val editRevision by rememberSaveable(id) { mutableStateOf(item?.revision) }
    var description by rememberSaveable(id) { mutableStateOf(item?.description ?: "") }
    var amount by rememberSaveable(id) { mutableStateOf(item?.amount_paisa?.let(::rawAmount) ?: "") }
    var date by rememberSaveable(id) { mutableStateOf(item?.next_date ?: LocalDate.now(BuildingDates.zone).toString()) }
    var category by rememberSaveable(id) { mutableStateOf(item?.category ?: "Other") }
    var frequency by rememberSaveable(id) { mutableStateOf(item?.frequency ?: "monthly") }
    var interval by rememberSaveable(id) { mutableStateOf((item?.interval_days ?: 30).toString()) }
    var scope by rememberSaveable(id) { mutableStateOf(state.book.flats.firstOrNull { it.id == item?.flat_id }?.flatNumber ?: "Building") }
    var active by rememberSaveable(id) { mutableStateOf(item?.active ?: true) }
    val request = rememberSaveable(id) { UUID.randomUUID().toString() }
    var attempted by rememberSaveable(id) { mutableStateOf(false) }
    DraftGuard(description != (item?.description ?: "") || amount != (item?.amount_paisa?.let(::rawAmount) ?: "") || date != (item?.next_date ?: LocalDate.now(BuildingDates.zone).toString()) || active != (item?.active ?: true) || frequency != (item?.frequency ?: "monthly") || category != (item?.category ?: "Other") || interval != (item?.interval_days ?: 30).toString() || scope != (state.book.flats.firstOrNull { it.id == item?.flat_id }?.flatNumber ?: "Building"), state.pendingRequestId != null)
    SectionCard(if (id == null) AppText.get(R.string.new_schedule) else AppText.get(R.string.edit_schedule)) {
    TextEntry(AppText.get(R.string.description), description) { description = it }; AmountEntry(AppText.get(R.string.amount), amount) { amount = it }
    DateEntry(AppText.get(R.string.next_due_date), date) { date = it }; ChoiceEntry(AppText.get(R.string.category), expenseCategories, category) { category = it }
    ChoiceEntry(AppText.get(R.string.frequency), listOf("monthly", "yearly", "weekly", "custom"), frequency) { frequency = it }
    if (frequency == "custom") TextEntry(AppText.get(R.string.repeat_every_days), DisplayLocale.digits(interval), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { interval = DisplayLocale.asciiDigits(it) }
    ChoiceEntry(AppText.get(R.string.scope), flatOptions(state), scope) { scope = it }
    Row { Checkbox(active, { active = it }, enabled = state.pendingRequestId == null); Text(AppText.get(R.string.active_schedule), Modifier.padding(top = 12.dp)) }
    val valid = description.isNotBlank() && (Money.parsePaisa(amount) ?: 0) > 0 && validDate(date) && (interval.toIntOrNull() ?: 0) > 0
    if (attempted && !valid) Text(AppText.get(R.string.add_a_description_positive_amount_valid_date_and_repeat_interval), color = MaterialTheme.colorScheme.error)
    SaveButton(state, AppText.get(R.string.save_schedule)) { attempted = true; if (valid) model.save("recurring_save", buildJsonObject {
        item?.let { put("id", it.id); put("revision", editRevision!!) }; put("description", description.trim())
        put("amount_paisa", Money.parsePaisa(amount)!!); put("next_date", date); put("category", category)
        put("frequency", frequency); put("interval_days", interval.toInt()); put("active", active)
        put("flat_id", flatId(state, scope)?.let(::JsonPrimitive) ?: JsonNull)
    }, request, onDone) }
    if (!state.saving && state.pendingRequestId == null) ConfirmAction(AppText.get(R.string.discard_schedule_draft), onDone)
    }
}

@Composable private fun RecurringPayment(state: BookUiState, model: BookViewModel, item: RecurringExpense, onDone: () -> Unit) {
    val scheduledDate = rememberSaveable(item.id) { item.next_date }
    val scheduleRevision = rememberSaveable(item.id) { item.revision }
    var date by rememberSaveable(item.id) { mutableStateOf(item.next_date) }
    var amount by rememberSaveable(item.id) { mutableStateOf(rawAmount(item.amount_paisa)) }
    var method by rememberSaveable(item.id) { mutableStateOf("Cash") }
    val request = rememberSaveable(item.id) { UUID.randomUUID().toString() }
    var attempted by rememberSaveable(item.id) { mutableStateOf(false) }
    DraftGuard(date != scheduledDate || amount != rawAmount(item.amount_paisa) || method != "Cash", state.pendingRequestId != null)
    SectionCard(AppText.get(R.string.record_paid_expense)) {
    Text(item.description, style = MaterialTheme.typography.titleMedium)
    DetailRow(AppText.get(R.string.scheduled_occurrence), recordDate(scheduledDate))
    Text(AppText.get(R.string.confirm_the_actual_payment_the_schedule_advances_only_after_it_is_saved), style = MaterialTheme.typography.bodyMedium)
    DateEntry(AppText.get(R.string.actual_payment_date), date) { date = it }; AmountEntry(AppText.get(R.string.actual_amount_paid), amount) { amount = it }
    ChoiceEntry(AppText.get(R.string.payment_method), listOf("Cash", "bKash", "Nagad", "Bank", "Other"), method) { method = it }
    val valid = validDate(date) && (Money.parsePaisa(amount) ?: 0) > 0
    if (attempted && !valid) Text(AppText.get(R.string.choose_a_valid_payment_date_and_positive_amount), color = MaterialTheme.colorScheme.error)
    SaveButton(state, AppText.get(R.string.confirm_paid_expense)) { attempted = true
        if (valid) model.save("expense_save", buildJsonObject {
            put("recurring_expense_id", item.id); put("scheduled_date", scheduledDate); put("recurring_revision", scheduleRevision)
            put("expense_date", date); put("category", item.category)
            put("description", item.description); put("amount_paisa", Money.parsePaisa(amount)!!)
            put("flat_id", item.flat_id?.let(::JsonPrimitive) ?: JsonNull); put("payment_method", method)
        }, request, onDone)
    }
    if (!state.saving && state.pendingRequestId == null) ConfirmAction(AppText.get(R.string.cancel_payment_entry), onDone)
    }
}
