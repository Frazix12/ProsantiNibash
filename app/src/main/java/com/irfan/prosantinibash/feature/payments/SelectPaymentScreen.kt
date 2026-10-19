package com.irfan.prosantinibash.feature.payments

import androidx.compose.runtime.Composable
import com.irfan.prosantinibash.R
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.core.ui.ActionRow
import com.irfan.prosantinibash.core.ui.BookListPage
import com.irfan.prosantinibash.core.ui.EmptyState
import com.irfan.prosantinibash.feature.book.BookUiState
import com.irfan.prosantinibash.feature.book.BookViewModel

@Composable
fun SelectPaymentScreen(state: BookUiState, model: BookViewModel, onAddTenant: () -> Unit = {}, onPayment: (String) -> Unit) {
    BookListPage(state, model::refresh) {
        if (state.book.tenancies.isEmpty()) item { EmptyState(AppText.get(R.string.no_tenants_yet), AppText.get(R.string.add_a_tenant_before_recording_rent), AppText.get(R.string.add_tenant), onAddTenant) }
        state.book.tenancies.sortedBy { it.status != "active" }.forEach { tenancy -> item(key = tenancy.id) {
            ActionRow("${state.book.flat(tenancy)?.flatNumber.orEmpty()} · ${state.book.tenant(tenancy)?.full_name.orEmpty()}",
                { onPayment(tenancy.id) }, R.drawable.ic_person,
                if (tenancy.status == "active") AppText.get(R.string.current_tenant) else AppText.get(R.string.previous_tenant))
        } }
    }
}
