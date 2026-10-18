package com.irfan.prosantinibash.feature.payments

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.RentPeriod
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.tenants.*
import java.time.LocalDate

@Composable
fun LedgerScreen(state: BookUiState, model: BookViewModel, tenancyId: String, onPaymentDetails: (String) -> Unit, onExport: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val tenancy = state.book.tenancies.firstOrNull { it.id == tenancyId }
    BookListPage(state, model::refresh) {
        item {
        SectionCard(AppText.get(R.string.balance)) {
        tenancy?.let { Text("${state.book.flat(it)?.flatNumber.orEmpty()} • ${state.book.tenant(it)?.full_name.orEmpty()}") }
        MoneyRow(AppText.get(R.string.rent_due), BookMath.due(state.book, tenancyId), true)
        MoneyRow(AppText.get(R.string.available_credit), BookMath.credit(state.book, tenancyId))
        }
        Button(onClick = { editing = "" }) { Text(AppText.get(R.string.add_historical_charge)) }
        ActionRow(AppText.get(R.string.export_ledger_pdf), onExport, R.drawable.ic_download)
        }
        if (editing != null) item(key = "editor-$editing") { key(editing) {
            ChargeEditor(state, model, tenancyId, state.book.rent_periods.firstOrNull { it.id == editing }) { editing = null }
        } }
        LedgerRows(state, tenancyId, onPaymentDetails) { editing = it }
    }
}

private fun LazyListScope.LedgerRows(state: BookUiState, tenancyId: String, onPayment: (String) -> Unit, onCharge: (String) -> Unit) {
    val charges = state.book.index.periods[tenancyId].orEmpty()
    val chargeById = charges.associateBy { it.id }
    val payments = state.book.payments.filter { it.tenancy_id == tenancyId }
    val paymentById = payments.associateBy { it.id }
    val entries = charges.map { Triple(it.period_date, it.id, true) } + payments.map { Triple(it.payment_date, it.id, false) }
    if (entries.isEmpty()) item { EmptyState(AppText.get(R.string.no_rent_history_yet), AppText.get(R.string.add_a_historical_charge_to_carry_earlier_records_into_this_ledger)) }
    items(entries.sortedWith(compareBy<Triple<String, String, Boolean>> { it.first }.thenBy { it.second }), key = { it.second }) { (date, id, charge) ->
        SectionCard {
        if (charge) chargeById[id]?.let { period ->
            Text("${DisplayLocale.date(date)} • ${appLabel(period.kind)}", style = MaterialTheme.typography.titleMedium)
            MoneyRow(AppText.get(R.string.charge), period.amount_paisa, true)
            MoneyRow(AppText.get(R.string.allocated_payments), BookMath.paid(state.book, id))
            period.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
            ActionRow(AppText.get(R.string.correct_charge), { onCharge(id) }, R.drawable.ic_edit)
        } else paymentById[id]?.let { payment ->
            ActionRow(paymentKindLabel(payment.kind), { onPayment(id) }, R.drawable.ic_receipt, "${DisplayLocale.date(date)} · ${appLabel(payment.payment_method)}")
            MoneyRow(AppText.get(R.string.amount), payment.amount_paisa, true)
            if (payment.voided) StatusBadge(AppText.get(R.string.voided))
        }
        }
    }
}

@Composable
private fun ChargeEditor(state: BookUiState, model: BookViewModel, tenancyId: String, charge: RentPeriod?, onDone: () -> Unit) {
    val initial = mapOf("id" to (charge?.id ?: ""), "revision" to (charge?.revision ?: 0).toString(),
        "tenancy_id" to tenancyId, "period_date" to (charge?.period_date ?: LocalDate.now(BuildingDates.zone).toString()),
        "amount_paisa" to paisaInput(charge?.amount_paisa ?: 0), "kind" to (charge?.kind ?: "manual"), "notes" to (charge?.notes ?: ""))
    val fields = rentForm(initial)
    SectionCard {
    DraftGuard(initial.any { (name, value) -> fields[name] != value }, state.pendingRequestId != null)
    Text(if (charge == null) AppText.get(R.string.historical_charge) else AppText.get(R.string.correct_rent_charge), style = MaterialTheme.typography.titleLarge)
    DateEntry(AppText.get(R.string.charge_date), fields.getValue("period_date")) { fields["period_date"] = it }
    AmountEntry(AppText.get(R.string.amount), fields.getValue("amount_paisa")) { fields["amount_paisa"] = it }
    if (charge == null) ChoiceEntry(AppText.get(R.string.charge_type), listOf("Historical charge", "Monthly rent"), if (fields["kind"] == "monthly") "Monthly rent" else "Historical charge") { fields["kind"] = if (it == "Monthly rent") "monthly" else "manual" }
    TextEntry(AppText.get(R.string.notes), fields.getValue("notes")) { fields["notes"] = it }
    fields["error"]?.takeIf { it.isNotBlank() }?.let { Text(appLabel(it), color = MaterialTheme.colorScheme.error) }
    SaveButton(state, AppText.get(R.string.save_charge)) {
        val amount = Money.parsePaisa(fields.getValue("amount_paisa"))
        fields["error"] = if (amount == null || (amount == 0L && fields["kind"] != "monthly")) "Enter a valid charge amount." else if (fields["period_date"].isNullOrBlank()) "Choose a charge date." else ""
        if (fields["error"].isNullOrEmpty()) model.save("rent_charge", rentPayload(fields, setOf("amount_paisa"), setOf("revision")), fields.getValue("request"), onDone)
    }
    TextButton(onClick = onDone, enabled = !state.saving && state.pendingRequestId == null) { Text(AppText.get(R.string.cancel)) }
    }
}
