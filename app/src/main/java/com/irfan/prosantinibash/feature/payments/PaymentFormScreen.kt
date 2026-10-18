package com.irfan.prosantinibash.feature.payments

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.tenants.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun PaymentFormScreen(state: BookUiState, model: BookViewModel, tenancyId: String,
    paymentId: String? = null, onDone: () -> Unit) {
    val payment = state.book.payments.firstOrNull { it.id == paymentId }
    val totalDue = BookMath.due(state.book, tenancyId)
    val initial = mapOf("id" to (paymentId ?: ""), "tenancy_id" to tenancyId,
        "revision" to (payment?.revision ?: 0).toString(), "amount_paisa" to paisaInput(payment?.amount_paisa ?: totalDue),
        "payment_date" to (payment?.payment_date ?: LocalDate.now(BuildingDates.zone).toString()),
        "through_month" to (payment?.through_month ?: YearMonth.now(BuildingDates.zone).atDay(1).toString()),
        "payment_method" to (payment?.payment_method ?: "Cash"), "reference" to (payment?.reference ?: ""),
        "notes" to (payment?.notes ?: ""), "kind" to (payment?.kind ?: "cash"))
    val fields = rentForm(initial)
    DraftGuard(initial.any { (name, value) -> fields[name] != value }, state.pendingRequestId != null)
    val due = BookMath.due(state.book, tenancyId, YearMonth.parse(fields.getValue("through_month").take(7)))
    BookPage(state, model::refresh) {
        PaymentContext(state, tenancyId, paymentId, due)
        PaymentFields(fields)
        PaymentOptions(fields, paymentId == null)
        val paying = Money.parsePaisa(fields.getValue("amount_paisa")) ?: 0
        if (paymentId == null && fields["kind"] == "cash") {
            SectionCard(AppText.get(R.string.allocation_preview)) {
                MoneyRow(AppText.get(R.string.applied_toward_selected_dues), minOf(paying, due))
                MoneyRow(if (paying <= due) AppText.get(R.string.remaining_selected_dues) else AppText.get(R.string.additional_credit), kotlin.math.abs(due - paying), true)
                Text(AppText.get(R.string.oldest_dues_first_through_the_selected_month), style = MaterialTheme.typography.bodySmall)
            }
        }
        SaveButton(state, if (paymentId == null) AppText.get(R.string.record_payment) else AppText.get(R.string.save_correction)) {
            fields["error"] = if (paying <= 0) "Enter an amount greater than zero." else if (fields["payment_date"].isNullOrBlank()) "Choose a payment date." else ""
            if (fields["error"].isNullOrEmpty()) model.save("payment_save", rentPayload(fields, setOf("amount_paisa"), setOf("revision")), fields.getValue("request"), onDone)
        }
    }
}

@Composable
private fun PaymentOptions(fields: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, creating: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) AppText.get(R.string.hide_additional_details) else AppText.get(R.string.reference_notes_and_historical_entries)) }
    if (expanded) {
        TextEntry(AppText.get(R.string.reference), fields.getValue("reference")) { fields["reference"] = it }
        TextEntry(AppText.get(R.string.notes), fields.getValue("notes")) { fields["notes"] = it }
        if (creating) ChoiceEntry(AppText.get(R.string.entry_type), paymentKinds.values.toList(), paymentKinds[fields.getValue("kind")].orEmpty()) { label -> fields["kind"] = paymentKinds.entries.first { it.value == label }.key }
        else DetailRow(AppText.get(R.string.entry_type), paymentKindLabel(fields.getValue("kind")))
        Text(AppText.get(R.string.opening_credit_is_brought_forward_not_cash_income), style = MaterialTheme.typography.bodySmall)
    }
}

internal val paymentKinds = linkedMapOf("cash" to "Rent payment", "opening_credit" to "Brought-forward credit", "credit_refund" to "Credit refund")
internal fun paymentKindLabel(kind: String) = appLabel(paymentKinds[kind] ?: kind.replace('_', ' '))

@Composable
private fun PaymentContext(state: BookUiState, tenancyId: String, paymentId: String?, due: Long) {
    val tenancy = state.book.tenancies.firstOrNull { it.id == tenancyId }
    val payment = state.book.payments.firstOrNull { it.id == paymentId }
    SectionCard(AppText.get(R.string.tenant_balance)) {
        Text(tenancy?.let { "${state.book.flat(it)?.flatNumber.orEmpty()} · ${state.book.tenant(it)?.full_name.orEmpty()}" } ?: AppText.get(R.string.tenant_not_found), style = MaterialTheme.typography.titleMedium)
        MoneyRow(AppText.get(R.string.due_through_selected_month), due, true)
        MoneyRow(AppText.get(R.string.available_credit), BookMath.credit(state.book, tenancyId))
        payment?.let { MoneyRow(AppText.get(R.string.currently_recorded), it.amount_paisa) }
    }
}

@Composable
private fun PaymentFields(fields: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>) {
    SectionCard(AppText.get(R.string.payment_details)) {
        AmountEntry(AppText.get(R.string.amount), fields.getValue("amount_paisa")) { fields["amount_paisa"] = it }
        fields["error"]?.takeIf { it.isNotEmpty() }?.let { Text(appLabel(it), color = MaterialTheme.colorScheme.error) }
        DateEntry(AppText.get(R.string.payment_date), fields.getValue("payment_date")) { fields["payment_date"] = it }
        Text(AppText.get(R.string.apply_to_dues_through), style = MaterialTheme.typography.labelLarge)
        MonthPicker(YearMonth.parse(fields.getValue("through_month").take(7))) { fields["through_month"] = it.atDay(1).toString() }
        ChoiceEntry(AppText.get(R.string.method), listOf("Cash", "bKash", "Nagad", "Bank Transfer", "Other"), fields.getValue("payment_method")) { fields["payment_method"] = it }
    }
}
