package com.irfan.prosantinibash.feature.deposits

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.data.model.Book
import java.math.BigInteger
import com.irfan.prosantinibash.feature.expenses.validDate
import com.irfan.prosantinibash.feature.expenses.recordDate
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.UUID
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun signedAmount(value: String): Long? = if (value.startsWith("-")) Money.parsePaisa(value.drop(1))?.let { -it } else Money.parsePaisa(value)

data class SettlementPreview(val due: Long, val credit: Long, val deposit: Long, val net: BigInteger)

/** Backdated departure cancels only generated monthly charges and releases their allocations. */
fun settlementPreview(book: Book, tenancyId: String, date: String, damage: Long, adjustment: Long): SettlementPreview {
    val removed = book.rent_periods.filter { it.tenancy_id == tenancyId && it.kind == "monthly" && it.period_date.take(7) > date.take(7) }
    val removedPaid = BookMath.sum(removed.map { BookMath.paid(book, it.id) })
    val due = Math.subtractExact(BookMath.due(book, tenancyId), BookMath.sum(removed.map { Math.subtractExact(it.amount_paisa, BookMath.paid(book, it.id)) }))
    val credit = Math.addExact(BookMath.credit(book, tenancyId), removedPaid)
    val deposit = BookMath.deposit(book, tenancyId)
    val net = BigInteger.valueOf(due).add(BigInteger.valueOf(damage)).add(BigInteger.valueOf(adjustment)).subtract(BigInteger.valueOf(credit)).subtract(BigInteger.valueOf(deposit))
    return SettlementPreview(due, credit, deposit, net)
}

@Composable fun DepositScreen(state: BookUiState, model: BookViewModel, tenancyId: String) {
    var amount by rememberSaveable(tenancyId) { mutableStateOf("") }
    var date by rememberSaveable(tenancyId) { mutableStateOf(LocalDate.now(BuildingDates.zone).toString()) }
    var type by rememberSaveable(tenancyId) { mutableStateOf("received") }
    var description by rememberSaveable(tenancyId) { mutableStateOf("") }
    var request by rememberSaveable(tenancyId) { mutableStateOf(UUID.randomUUID().toString()) }
    var attempted by rememberSaveable(tenancyId) { mutableStateOf(false) }
    val paisa = if (type == "adjustment") signedAmount(amount) else Money.parsePaisa(amount)
    val valid = paisa != null && paisa != 0L && (type == "adjustment" || paisa > 0) && validDate(date)
DraftGuard(amount.isNotBlank() || description.isNotBlank() || type != "received" || date != LocalDate.now(BuildingDates.zone).toString(), state.pendingRequestId != null)
    BookListPage(state, model::refresh) {
        item {
        SectionCard { MoneyRow(AppText.get(R.string.deposit_held), BookMath.deposit(state.book, tenancyId), true)
            Text(AppText.get(R.string.deposits_are_held_separately_from_rent_income), style = MaterialTheme.typography.bodyMedium) }
        SectionCard(AppText.get(R.string.add_transaction)) {
        ChoiceEntry(AppText.get(R.string.transaction), listOf("received", "deduction", "refund", "adjustment"), type) { type = it }
        AmountEntry(AppText.get(R.string.amount_adjustment_may_be_negative), amount) { amount = it }; DateEntry(AppText.get(R.string.date), date) { date = it }
        TextEntry(AppText.get(R.string.description), description) { description = it }
        if (attempted && !valid) Text(AppText.get(R.string.enter_a_valid_nonzero_amount_and_date_only_adjustments_may_be_negative), color = MaterialTheme.colorScheme.error)
        SaveButton(state, AppText.get(R.string.record_deposit_transaction)) { attempted = true; if (valid) model.save("deposit_save", buildJsonObject {
            put("tenancy_id", tenancyId); put("type", type); put("amount_paisa", paisa!!); put("transaction_date", date); put("description", description.trim())
        }, request) { amount = ""; description = ""; attempted = false; type = "received"; date = LocalDate.now(BuildingDates.zone).toString(); request = UUID.randomUUID().toString() } }
        }
        SectionHeading(AppText.get(R.string.transaction_history))
        if (state.book.deposit_transactions.none { it.tenancy_id == tenancyId }) EmptyState(AppText.get(R.string.no_deposit_transactions), AppText.get(R.string.record_a_deposit_when_it_is_received))
        }
        items(state.book.index.deposits[tenancyId].orEmpty().sortedByDescending { it.transaction_date }, key = { it.id }) {
            SectionCard {
                val signed = if (it.type in listOf("deduction", "refund")) -it.amount_paisa else it.amount_paisa
                MoneyRow(appLabel(it.type.replaceFirstChar(Char::uppercaseChar)), signed)
                Text(recordDate(it.transaction_date), style = MaterialTheme.typography.labelMedium)
                it.description?.takeIf(String::isNotBlank)?.let { note -> Text(note, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

@Composable fun MoveOutScreen(state: BookUiState, model: BookViewModel, tenancyId: String, onDone: () -> Unit) {
    val tenancy = state.book.tenancies.firstOrNull { it.id == tenancyId }
    val editRevision by rememberSaveable(tenancyId) { mutableStateOf(tenancy?.revision) }
    var date by rememberSaveable(tenancyId) { mutableStateOf(LocalDate.now(BuildingDates.zone).toString()) }
    var damage by rememberSaveable(tenancyId) { mutableStateOf("0") }
    var adjustment by rememberSaveable(tenancyId) { mutableStateOf("0") }
    var notes by rememberSaveable(tenancyId) { mutableStateOf("") }
    val request = rememberSaveable(tenancyId) { UUID.randomUUID().toString() }
    var attempted by rememberSaveable(tenancyId) { mutableStateOf(false) }
    var confirming by rememberSaveable(tenancyId) { mutableStateOf(false) }
    val damagePaisa = Money.parsePaisa(damage); val adjustmentPaisa = signedAmount(adjustment)
    val valid = tenancy?.status == "active" && validDate(date) && date >= tenancy.move_in_date && date <= LocalDate.now(BuildingDates.zone).toString() && damagePaisa != null && adjustmentPaisa != null
    BookPage(state, model::refresh) {
        DraftGuard(damage != "0" || adjustment != "0" || notes.isNotBlank() || date != LocalDate.now(BuildingDates.zone).toString(), state.pendingRequestId != null)
        SectionCard(AppText.get(R.string.closing_tenancy)) {
            Text(tenancy?.let { state.book.tenant(it)?.full_name } ?: AppText.get(R.string.tenant), style = MaterialTheme.typography.titleLarge)
            Text(tenancy?.let { state.book.flat(it)?.flatNumber }?.let { AppText.get(R.string.flat_value, it) } ?: "", style = MaterialTheme.typography.bodyMedium)
            DateEntry(AppText.get(R.string.move_out_date), date) { date = it }
            AmountEntry(AppText.get(R.string.damage_deduction), damage) { damage = it }
            AmountEntry(AppText.get(R.string.other_adjustment_owed_credit), adjustment) { adjustment = it }
            TextEntry(AppText.get(R.string.settlement_notes), notes) { notes = it }
            if (attempted && !valid) Text(AppText.get(R.string.use_valid_amounts_and_a_date_between_move_in_and_today_for_an_active_tenanc), color = MaterialTheme.colorScheme.error)
        }
        val preview = settlementPreview(state.book, tenancyId, date, damagePaisa ?: 0, adjustmentPaisa ?: 0)
        SettlementSummary(preview, damagePaisa ?: 0, adjustmentPaisa ?: 0)
        Text(AppText.get(R.string.last_month_rent_defaults_to_full_rent_adjust_it_in_the_rent_ledger_before_c), style = MaterialTheme.typography.bodyMedium)
        SaveButton(state, AppText.get(R.string.review_and_confirm_move_out)) { attempted = true; if (valid) confirming = true }
        if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(AppText.get(R.string.close_this_tenancy)) },
            text = { Text(AppText.get(R.string.this_settles_credit_and_deposit_retains_unpaid_debt_under_this_tenant_and_m)) },
            confirmButton = { TextButton(onClick = { confirming = false; if (valid) model.save("move_out", buildJsonObject {
                put("id", tenancyId); put("revision", editRevision!!); put("move_out_date", date)
                put("damage_paisa", damagePaisa!!); put("adjustment_paisa", adjustmentPaisa!!); put("notes", notes.trim())
            }, request, onDone) }) { Text(AppText.get(R.string.confirm_move_out)) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(AppText.get(R.string.keep_tenancy)) } })
    }
}

@Composable private fun SettlementSummary(preview: SettlementPreview, damage: Long, adjustment: Long) {
    SectionCard(AppText.get(R.string.settlement_review)) {
        MoneyRow(AppText.get(R.string.unpaid_rent), preview.due)
        MoneyRow(AppText.get(R.string.available_rent_credit), preview.credit)
        MoneyRow(AppText.get(R.string.deposit_held), preview.deposit)
        MoneyRow(AppText.get(R.string.damage_deduction), damage)
        MoneyRow(AppText.get(R.string.other_adjustment), adjustment)
        HorizontalDivider()
        val retained = preview.net.signum() > 0
        Text(if (retained) AppText.get(R.string.debt_retained_for_this_tenant) else AppText.get(R.string.refund_to_tenant), style = MaterialTheme.typography.titleMedium)
        Text("৳${DisplayLocale.digits(java.math.BigDecimal(preview.net.abs(), 2).toPlainString())}", style = MaterialTheme.typography.headlineMedium)
    }
}
