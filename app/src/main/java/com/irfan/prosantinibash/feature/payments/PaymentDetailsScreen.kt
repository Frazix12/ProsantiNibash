package com.irfan.prosantinibash.feature.payments

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import java.time.YearMonth
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.Money
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.tenants.rentForm
import kotlinx.serialization.json.*

@Composable
fun PaymentDetailsScreen(state: BookUiState, model: BookViewModel, paymentId: String,
    onEdit: (String) -> Unit, onReceipt: (String) -> Unit, onAttachment: (String) -> Unit) {
    val payment = state.book.payments.firstOrNull { it.id == paymentId }
    val tenancy = payment?.let { p -> state.book.tenancies.firstOrNull { it.id == p.tenancy_id } }
    val fields = rentForm(emptyMap())
    BookPage(state, model::refresh) {
        if (payment == null) EmptyState(AppText.get(R.string.payment_unavailable), AppText.get(R.string.refresh_to_check_this_record_again)) else {
            SectionCard {
            Text(payment.receipt_number, style = MaterialTheme.typography.headlineSmall)
            Text(Money.format(payment.amount_paisa), style = MaterialTheme.typography.headlineLarge)
            tenancy?.let { Text("${state.book.flat(it)?.flatNumber.orEmpty()} • ${state.book.tenant(it)?.full_name.orEmpty()}") }
            DetailRow(AppText.get(R.string.payment_date), DisplayLocale.date(payment.payment_date))
            DetailRow(AppText.get(R.string.method), appLabel(payment.payment_method))
            DetailRow(AppText.get(R.string.through_month), DisplayLocale.month(YearMonth.parse(payment.through_month.take(7))))
            DetailRow(AppText.get(R.string.entry_type), paymentKindLabel(payment.kind))
            listOf(payment.reference, payment.notes).filterNotNull().filter { it.isNotBlank() }.forEach { Text(it) }
            if (payment.voided) StatusBadge(AppText.get(R.string.voided)) else if (payment.revision > 1) StatusBadge(AppText.get(R.string.corrected))
            }
            SectionCard(AppText.get(R.string.receipt_attachment)) {
                ActionRow(AppText.get(R.string.receipt_and_share), { onReceipt(payment.id) }, R.drawable.ic_receipt)
                ActionRow(if (payment.attachment_path == null) AppText.get(R.string.add_attachment) else AppText.get(R.string.view_or_replace_attachment), { onAttachment(payment.id) }, R.drawable.ic_document)
            }
            if (!payment.voided) {
                if (!state.saving) ActionRow(AppText.get(R.string.correct_payment), { onEdit(payment.id) }, R.drawable.ic_edit)
                if (!state.saving) ConfirmAction(AppText.get(R.string.void_payment)) {
                    model.save("payment_void", buildJsonObject { put("id", payment.id); put("revision", payment.revision) }, fields.getValue("request"))
                }
            }
            Text(AppText.get(R.string.corrections_retain_the_original_receipt_and_audit_history), style = MaterialTheme.typography.bodySmall)
        }
    }
}
