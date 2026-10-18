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
import com.irfan.prosantinibash.core.util.Money
import com.irfan.prosantinibash.feature.book.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.YearMonth
import com.irfan.prosantinibash.core.util.BuildingDates
import java.util.UUID
import androidx.compose.ui.res.painterResource
import com.irfan.prosantinibash.data.model.Expense

val expenseCategories = listOf("Electricity", "Water", "Cleaning", "Security", "Plumbing", "Electrical", "Lift", "Generator", "Painting", "Repair", "Internet / CCTV", "Other")
fun rawAmount(paisa: Long) = java.math.BigDecimal.valueOf(paisa, 2).toPlainString()
fun recordDate(value: String) = runCatching { LocalDate.parse(value).format(DisplayLocale.formatter("d MMM yyyy")) }.getOrDefault(value)
fun validDate(value: String) = runCatching { LocalDate.parse(value) }.isSuccess
fun flatOptions(state: BookUiState) = listOf("Building") + state.book.flats.map { it.flatNumber }
fun flatId(state: BookUiState, label: String) = state.book.flats.firstOrNull { it.flatNumber == label }?.id

@Composable fun ExpensesScreen(state: BookUiState, model: BookViewModel, onEdit: (String?) -> Unit, onFiles: (String) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now(BuildingDates.zone).toString()) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var scope by rememberSaveable { mutableStateOf("All") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val monthly = remember(state.book, month) {
        state.book.expenses.filter { !it.voided && it.expense_date.startsWith(month) }.sortedByDescending { it.expense_date }
    }
    val total = remember(monthly) { com.irfan.prosantinibash.core.util.BookMath.sum(monthly.map { it.amount_paisa }) }
    val expenses = remember(monthly, query, category, scope, state.book.flats) {
        monthly.filter { (category == "All" || it.category == category) &&
            (scope == "All" || it.flat_id == flatId(state, scope)) &&
            "${it.description} ${it.category} ${appLabel(it.category)} ${it.vendor.orEmpty()}".contains(query.trim(), true) }
    }
    val filtered = query.isNotBlank() || category != "All" || scope != "All"
    BookListPage(state, model::refresh) {
        item { MonthPicker(YearMonth.parse(month)) { month = it.toString() } }
        item { SectionCard {
            Text(AppText.get(R.string.total_spent_this_month), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(total), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(AppText.get(R.string.value_expenses, monthly.size), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(AppText.get(R.string.add_expense))
            }
        } }
        item {
            TextEntry(AppText.get(R.string.search_expenses), query) { query = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) AppText.get(R.string.hide_filters) else AppText.get(R.string.filters)) }
                if (filtered) TextButton(onClick = { query = ""; category = "All"; scope = "All" }) { Text(AppText.get(R.string.clear_filters)) }
            }
            if (showFilters) {
                ChoiceEntry(AppText.get(R.string.category), listOf("All") + (expenseCategories + monthly.map { it.category }).distinct(), category) { category = it }
                ChoiceEntry(AppText.get(R.string.scope), listOf("All") + flatOptions(state), scope) { scope = it }
            }
            if (filtered) Text(listOfNotNull(AppText.get(R.string.value_results, expenses.size), category.takeUnless { it == "All" }?.let(::appLabel),
                scope.takeUnless { it == "All" }?.let(::appLabel)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (expenses.isEmpty()) item { EmptyState(if (filtered) AppText.get(R.string.no_matching_expenses) else AppText.get(R.string.no_expenses_this_month),
            if (filtered) AppText.get(R.string.change_your_search_or_clear_the_filters) else AppText.get(R.string.use_add_expense_to_record_a_payment)) }
        items(expenses, key = { it.id }) { ExpenseRecord(state, model, it, onEdit, onFiles) }
    }
}

@Composable private fun ExpenseRecord(state: BookUiState, model: BookViewModel, expense: Expense,
    onEdit: (String?) -> Unit, onFiles: (String) -> Unit) {
    val voidRequest = rememberSaveable(expense.id, expense.revision) { UUID.randomUUID().toString() }
    val maintenance = state.book.maintenance.firstOrNull { it.expense_id == expense.id }
    SectionCard {
        Text(expense.description, style = MaterialTheme.typography.titleMedium)
        Text(Money.format(expense.amount_paisa), style = MaterialTheme.typography.titleLarge)
        Text("${recordDate(expense.expense_date)} · ${appLabel(expense.category)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.book.flats.firstOrNull { it.id == expense.flat_id }?.flatNumber?.let { AppText.get(R.string.flat_value, it) } ?: AppText.get(R.string.building),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (maintenance != null) StatusBadge(AppText.get(R.string.maintenance_counted_once))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (maintenance == null) TextButton(onClick = { onEdit(expense.id) },
                enabled = !state.saving && state.pendingRequestId == null) { Text(AppText.get(R.string.edit_expense)) }
            RecordActions(enabled = !state.saving && state.pendingRequestId == null,
                onEdit = null,
                onFiles = { onFiles(if (maintenance != null) "maintenance/${maintenance.id}" else "expense/${expense.id}") },
                onVoid = if (maintenance == null) ({ model.save("expense_void", buildJsonObject {
                    put("id", expense.id); put("revision", expense.revision)
                }, voidRequest) }) else null)
        }
    }
}

@Composable fun RecordActions(enabled: Boolean, onEdit: (() -> Unit)?, onFiles: () -> Unit, onVoid: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Icon(painterResource(R.drawable.ic_overflow), null); Text(AppText.get(R.string.actions)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            onEdit?.let { DropdownMenuItem(text = { Text(AppText.get(R.string.edit_record)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) }, onClick = { expanded = false; it() }) }
            DropdownMenuItem(text = { Text(AppText.get(R.string.view_receipt_attachment)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_receipt), null) }, onClick = { expanded = false; onFiles() })
            onVoid?.let { DropdownMenuItem(text = { Text(AppText.get(R.string.void_record)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) }, onClick = { expanded = false; confirming = true }) }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(AppText.get(R.string.void_this_record)) },
        text = { Text(AppText.get(R.string.it_will_no_longer_count_in_totals_the_change_remains_in_audit_history)) },
        confirmButton = { TextButton(onClick = { confirming = false; onVoid?.invoke() }) { Text(AppText.get(R.string.void_record)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(AppText.get(R.string.keep_record)) } })
}

@Composable fun ExpenseFormScreen(state: BookUiState, model: BookViewModel, expenseId: String? = null, onDone: () -> Unit) {
    val expense = state.book.expenses.firstOrNull { it.id == expenseId }
    val editRevision by rememberSaveable(expenseId) { mutableStateOf(expense?.revision) }
    var amount by rememberSaveable(expenseId) { mutableStateOf(expense?.amount_paisa?.let(::rawAmount) ?: "") }
    var date by rememberSaveable(expenseId) { mutableStateOf(expense?.expense_date ?: LocalDate.now(com.irfan.prosantinibash.core.util.BuildingDates.zone).toString()) }
    var category by rememberSaveable(expenseId) { mutableStateOf(expense?.category ?: "Other") }
    var description by rememberSaveable(expenseId) { mutableStateOf(expense?.description ?: "") }
    var scope by rememberSaveable(expenseId) { mutableStateOf(state.book.flats.firstOrNull { it.id == expense?.flat_id }?.flatNumber ?: "Building") }
    var vendor by rememberSaveable(expenseId) { mutableStateOf(expense?.vendor ?: "") }
    var method by rememberSaveable(expenseId) { mutableStateOf(expense?.payment_method ?: "Cash") }
    var notes by rememberSaveable(expenseId) { mutableStateOf(expense?.notes ?: "") }
    var duplicate by rememberSaveable(expenseId) { mutableStateOf(false) }
    var request by rememberSaveable(expenseId) { mutableStateOf(UUID.randomUUID().toString()) }
    var attempted by rememberSaveable(expenseId) { mutableStateOf(false) }
    val valid = (Money.parsePaisa(amount) ?: 0) > 0 && validDate(date) && description.isNotBlank()
    BookPage(state, model::refresh) {
        if (expenseId != null && expense == null) { Text(AppText.get(R.string.expense_no_longer_available_go_back_and_refresh)); return@BookPage }
        if (!duplicate && expenseId != null && state.book.maintenance.any { it.expense_id == expenseId }) { Text(AppText.get(R.string.this_expense_is_linked_to_maintenance_edit_it_through_maintenance)); return@BookPage }
        DraftGuard(amount != (expense?.amount_paisa?.let(::rawAmount) ?: "") || description != (expense?.description ?: "") || date != (expense?.expense_date ?: LocalDate.now(BuildingDates.zone).toString()) || category != (expense?.category ?: "Other") || vendor != (expense?.vendor ?: "") || notes != (expense?.notes ?: "") || scope != (state.book.flats.firstOrNull { it.id == expense?.flat_id }?.flatNumber ?: "Building") || method != (expense?.payment_method ?: "Cash") || duplicate, state.pendingRequestId != null)
        if (expense != null && !duplicate) TextButton(onClick = { duplicate = true; request = UUID.randomUUID().toString(); date = LocalDate.now(com.irfan.prosantinibash.core.util.BuildingDates.zone).toString() }, enabled = state.pendingRequestId == null && !state.saving) { Icon(painterResource(R.drawable.ic_repeat), null); Text(AppText.get(R.string.duplicate_as_a_new_expense)) }
        SectionCard(AppText.get(R.string.payment_details)) {
        AmountEntry(AppText.get(R.string.amount), amount) { amount = it }; if (attempted && (Money.parsePaisa(amount) ?: 0) <= 0) Text(AppText.get(R.string.enter_an_amount_greater_than_zero), color = MaterialTheme.colorScheme.error)
        DateEntry(AppText.get(R.string.paid_date), date) { date = it }; if (attempted && !validDate(date)) Text(AppText.get(R.string.choose_a_valid_payment_date), color = MaterialTheme.colorScheme.error)
        ChoiceEntry(AppText.get(R.string.category), expenseCategories, category) { category = it }; TextEntry(AppText.get(R.string.description), description) { description = it }
        if (attempted && description.isBlank()) Text(AppText.get(R.string.add_a_short_description), color = MaterialTheme.colorScheme.error)
        ChoiceEntry(AppText.get(R.string.scope), flatOptions(state), scope) { scope = it }
        ChoiceEntry(AppText.get(R.string.payment_method), listOf("Cash", "bKash", "Nagad", "Bank", "Other"), method) { method = it } }
        SectionCard(AppText.get(R.string.optional_details)) { TextEntry(AppText.get(R.string.vendor), vendor) { vendor = it }; TextEntry(AppText.get(R.string.notes), notes) { notes = it } }
        SaveButton(state, AppText.get(R.string.save_expense)) { attempted = true; if (valid) model.save("expense_save", buildJsonObject {
            if (!duplicate) expense?.let { put("id", it.id); put("revision", editRevision!!) }
            put("amount_paisa", Money.parsePaisa(amount)!!); put("expense_date", date); put("category", category)
            put("description", description.trim()); put("flat_id", flatId(state, scope)?.let(::JsonPrimitive) ?: JsonNull)
            put("vendor", vendor.trim()); put("payment_method", method); put("notes", notes.trim())
        }, request, onDone) }
    }
}
