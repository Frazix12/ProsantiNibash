package com.irfan.prosantinibash.feature.tenants

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.input.KeyboardType
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.BuildingDates
import com.irfan.prosantinibash.core.util.Money
import com.irfan.prosantinibash.feature.book.*
import java.time.LocalDate

@Composable
fun TenancyFormScreen(state: BookUiState, model: BookViewModel, flatId: String,
    tenancyId: String? = null, onDone: () -> Unit) {
    val tenancy = state.book.tenancies.firstOrNull { it.id == tenancyId }
    val tenant = tenancy?.let(state.book::tenant)
    val today = LocalDate.now(BuildingDates.zone).toString()
    val initial = mapOf("id" to (tenancyId ?: ""), "flat_id" to flatId,
        "tenant_id" to (tenant?.id ?: ""), "revision" to (tenancy?.revision ?: 0).toString(),
        "tenant_revision" to (tenant?.revision ?: 0).toString(), "full_name" to (tenant?.full_name ?: ""),
        "phone" to (tenant?.phone ?: ""), "emergency_phone" to (tenant?.emergency_phone ?: ""),
        "occupation" to (tenant?.occupation ?: ""), "permanent_address" to (tenant?.permanent_address ?: ""),
        "notes" to (tenant?.notes ?: ""), "monthly_rent_paisa" to paisaInput(tenancy?.monthly_rent_paisa ?: state.book.flats.firstOrNull { it.id == flatId }?.defaultRentPaisa ?: 0),
        "move_in_date" to (tenancy?.move_in_date ?: today), "ledger_start_date" to (tenancy?.ledger_start_date ?: today),
        "agreement_start_date" to (tenancy?.agreement_start_date ?: ""),
        "agreement_end_date" to (tenancy?.agreement_end_date ?: ""), "opening_due_paisa" to "0", "opening_credit_paisa" to "0")
    val fields = rentForm(initial)
    DraftGuard(initial.any { (key, value) -> fields[key] != value }, state.pendingRequestId != null)
    BookPage(state, model::refresh) {
        SectionCard(AppText.get(R.string.tenant_details)) {
            Text(AppText.get(R.string.flat_value, state.book.flats.firstOrNull { it.id == flatId }?.flatNumber.orEmpty()),
                style = MaterialTheme.typography.titleMedium)
            TextEntry(AppText.get(R.string.full_name), fields["full_name"].orEmpty()) { fields["full_name"] = it }
            if (fields["error"] == "Enter the tenant's name.") Text(appLabel(fields.getValue("error")), color = MaterialTheme.colorScheme.error)
            TextEntry(AppText.get(R.string.phone_optional), fields["phone"].orEmpty(), keyboardType = KeyboardType.Phone) { fields["phone"] = it }
        }
        SectionCard(AppText.get(R.string.rent_and_dates)) { TenancyFields(fields, tenancyId == null) }
        SectionCard { PersonalFields(fields) }
        fields["error"]?.let { Text(appLabel(it), color = MaterialTheme.colorScheme.error) }
        SaveButton(state, AppText.get(R.string.save_tenant)) {
            fields["error"] = tenancyError(fields)
            if (fields["error"].isNullOrEmpty()) model.save("tenancy_save", rentPayload(fields,
                setOf("monthly_rent_paisa", "opening_due_paisa", "opening_credit_paisa"),
                setOf("revision", "tenant_revision")), fields.getValue("request"), onDone)
        }
    }
}

@Composable
private fun PersonalFields(fields: SnapshotStateMap<String, String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(AppText.get(R.string.contact_and_notes)) }
    if (expanded) listOf("emergency_phone" to AppText.get(R.string.emergency_phone), "occupation" to AppText.get(R.string.occupation),
        "permanent_address" to AppText.get(R.string.permanent_address), "notes" to AppText.get(R.string.notes)).forEach { (key, label) ->
        TextEntry(label, fields[key].orEmpty(), keyboardType = if (key == "emergency_phone") KeyboardType.Phone else KeyboardType.Text) { fields[key] = it }
    }
}

@Composable
private fun TenancyFields(fields: SnapshotStateMap<String, String>, creating: Boolean) {
    AmountEntry(AppText.get(R.string.monthly_rent), fields.getValue("monthly_rent_paisa")) { fields["monthly_rent_paisa"] = it }
    if (fields["error"] == "Enter a positive monthly rent.") Text(appLabel(fields.getValue("error")), color = MaterialTheme.colorScheme.error)
    DateEntry(AppText.get(R.string.move_in_date), fields.getValue("move_in_date")) { fields["move_in_date"] = it }
    if (creating) DateEntry(AppText.get(R.string.ledger_starts), fields.getValue("ledger_start_date")) { fields["ledger_start_date"] = it }
    else Text(AppText.get(R.string.ledger_starts_value, DisplayLocale.date(fields.getValue("ledger_start_date"))))
    var details by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { details = !details }) { Text(if (creating) AppText.get(R.string.agreement_and_opening_balance) else AppText.get(R.string.agreement_details)) }
    if (details) {
        DateEntry(AppText.get(R.string.agreement_starts), fields.getValue("agreement_start_date"), optional = true) { fields["agreement_start_date"] = it }
        DateEntry(AppText.get(R.string.agreement_ends), fields.getValue("agreement_end_date"), optional = true) { fields["agreement_end_date"] = it }
        if (creating) {
            Text(AppText.get(R.string.opening_balance_covers_history_before_the_ledger_starts))
            AmountEntry(AppText.get(R.string.previous_due), fields.getValue("opening_due_paisa")) { fields["opening_due_paisa"] = it }
            AmountEntry(AppText.get(R.string.previous_credit), fields.getValue("opening_credit_paisa")) { fields["opening_credit_paisa"] = it }
        }
    }
}

internal fun tenancyError(fields: Map<String, String>): String {
    if (fields["full_name"].isNullOrBlank()) return "Enter the tenant's name."
    if ((Money.parsePaisa(fields["monthly_rent_paisa"].orEmpty()) ?: 0) <= 0) return "Enter a positive monthly rent."
    val start = runCatching { LocalDate.parse(fields["move_in_date"]) }.getOrNull() ?: return "Choose a move-in date."
    val ledger = runCatching { LocalDate.parse(fields["ledger_start_date"]) }.getOrNull() ?: return "Choose a ledger start date."
    if (ledger < start) return "Ledger start cannot precede move-in."
    for (key in listOf("opening_due_paisa", "opening_credit_paisa")) {
        if (Money.parsePaisa(fields[key].orEmpty()) == null) return "Enter a valid opening amount."
    }
    if ((Money.parsePaisa(fields.getValue("opening_due_paisa")) ?: 0) > 0 && (Money.parsePaisa(fields.getValue("opening_credit_paisa")) ?: 0) > 0)
        return "Enter either opening due or credit."
    for (key in listOf("agreement_start_date", "agreement_end_date")) {
        val date = fields[key].orEmpty()
        if (date.isNotBlank() && runCatching { LocalDate.parse(date) }.isFailure) return "Choose a valid agreement date."
    }
    val agreementStart = fields["agreement_start_date"].orEmpty().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val agreementEnd = fields["agreement_end_date"].orEmpty().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (agreementStart != null && agreementEnd != null && agreementEnd < agreementStart) return "Agreement end must follow its start."
    return ""
}
