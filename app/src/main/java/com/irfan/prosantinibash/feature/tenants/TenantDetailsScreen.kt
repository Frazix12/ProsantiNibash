package com.irfan.prosantinibash.feature.tenants

import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*

@Composable
fun TenantDetailsScreen(state: BookUiState, model: BookViewModel, tenancyId: String,
    onPayment: (String) -> Unit, onDocuments: (String) -> Unit, onDeposit: (String) -> Unit,
    onMoveOut: (String) -> Unit, onEdit: (String) -> Unit, onLedger: (String) -> Unit) {
    val tenancy = state.book.tenancies.firstOrNull { it.id == tenancyId }
    val tenant = tenancy?.let(state.book::tenant)
    BookPage(state, model::refresh) {
        if (tenancy == null || tenant == null) EmptyState(AppText.get(R.string.tenant_unavailable), AppText.get(R.string.refresh_the_building_to_try_again)) else {
            TenantIdentity(tenant, tenancy, state.book.flat(tenancy)?.flatNumber.orEmpty())
            SectionCard(AppText.get(R.string.rent_and_balances)) {
                MoneyRow(AppText.get(R.string.monthly_rent), tenancy.monthly_rent_paisa)
                MoneyRow(AppText.get(R.string.outstanding_rent), BookMath.due(state.book, tenancyId), emphasis = true)
                MoneyRow(AppText.get(R.string.available_credit), BookMath.credit(state.book, tenancyId))
                MoneyRow(AppText.get(R.string.deposit_balance), BookMath.deposit(state.book, tenancyId))
            }
            Button(onClick = { onPayment(tenancyId) }, modifier = Modifier.fillMaxWidth()) { Text(AppText.get(R.string.record_payment)) }
            TenantContact(tenant)
            TenancyDates(tenancy)
            SectionCard(AppText.get(R.string.manage_tenant)) {
                ActionRow(AppText.get(R.string.rent_ledger), { onLedger(tenancyId) }, R.drawable.ic_receipt)
                ActionRow(AppText.get(R.string.photo_and_documents), { onDocuments(tenant.id) }, R.drawable.ic_document)
                ActionRow(AppText.get(R.string.deposit_history), { onDeposit(tenancyId) }, R.drawable.ic_receipt)
                ActionRow(AppText.get(R.string.edit_tenant), { onEdit(tenancyId) }, R.drawable.ic_edit)
            }
            if (tenancy.status == "active") SectionCard(AppText.get(R.string.tenancy_settlement)) {
                ActionRow(AppText.get(R.string.complete_move_out), { onMoveOut(tenancyId) }, R.drawable.ic_next,
                    AppText.get(R.string.review_rent_deposit_and_any_remaining_balance_before_closing))
            }
        }
    }
}

@Composable
private fun TenantIdentity(tenant: Tenant, tenancy: Tenancy, flatNumber: String) {
    SectionCard {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(tenant.full_name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString(""),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(tenant.full_name, style = MaterialTheme.typography.headlineSmall)
        Text(AppText.get(R.string.flat_value, flatNumber), color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusBadge(if (tenancy.status == "active") AppText.get(R.string.current_tenant) else AppText.get(R.string.previous_tenant))
    }
}

@Composable
private fun TenantContact(tenant: Tenant) {
    val context = LocalContext.current
    var callError by remember { mutableStateOf(false) }
    SectionCard(AppText.get(R.string.contact_and_notes)) {
        tenant.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            ActionRow(AppText.get(R.string.call_value, phone), {
                callError = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }.isFailure
            }, R.drawable.ic_phone)
        }
        if (callError) Text(AppText.get(R.string.no_phone_app_is_available_on_this_device), color = MaterialTheme.colorScheme.error)
        listOf(AppText.get(R.string.emergency_phone) to tenant.emergency_phone, AppText.get(R.string.occupation) to tenant.occupation,
            AppText.get(R.string.permanent_address) to tenant.permanent_address, AppText.get(R.string.notes) to tenant.notes)
            .filter { !it.second.isNullOrBlank() }.forEach { (label, value) -> DetailRow(label, value.orEmpty()) }
        if (listOf(tenant.phone, tenant.emergency_phone, tenant.occupation, tenant.permanent_address, tenant.notes)
                .all { it.isNullOrBlank() }) Text(AppText.get(R.string.no_contact_details_added), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TenancyDates(tenancy: Tenancy) {
    SectionCard(AppText.get(R.string.tenancy_and_agreement)) {
        DetailRow(AppText.get(R.string.moved_in), readableDate(tenancy.move_in_date))
        DetailRow(AppText.get(R.string.ledger_starts), readableDate(tenancy.ledger_start_date))
        tenancy.move_out_date?.let { DetailRow(AppText.get(R.string.moved_out), readableDate(it)) }
        tenancy.agreement_start_date?.let { DetailRow(AppText.get(R.string.agreement_starts), readableDate(it)) }
        tenancy.agreement_end_date?.let { DetailRow(AppText.get(R.string.agreement_ends), readableDate(it)) }
    }
}

private fun readableDate(value: String): String = runCatching {
    java.time.LocalDate.parse(value).format(DisplayLocale.formatter("d MMM yyyy"))
}.getOrDefault(value)
