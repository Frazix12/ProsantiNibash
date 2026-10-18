package com.irfan.prosantinibash.feature.reports

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
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*
import java.time.YearMonth

@Composable fun ReportsScreen(state: BookUiState, model: BookViewModel, onExport: (String) -> Unit, onTenant: (String) -> Unit) {
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now(BuildingDates.zone).toString()) }
    var mode by rememberSaveable { mutableStateOf("Monthly") }
    val month = YearMonth.parse(selectedMonth)
    val outstanding = remember(state.book) { state.book.tenancies.map { it to BookMath.due(state.book, it.id) }
        .filter { it.second > 0 }.sortedByDescending { it.second } }
    BookListPage(state, model::refresh) {
        item {
            ChoiceEntry(AppText.get(R.string.report), listOf("Monthly", "Annual", "Outstanding", "Flat ledger"), mode) { mode = it }
            if (mode == "Monthly" || mode == "Annual") MonthPicker(month) { selectedMonth = it.toString() }
        }
        when (mode) {
            "Monthly" -> item { MonthlyReport(state.book, month) }
            "Annual" -> item { AnnualReport(state.book, month.year) }
            "Outstanding" -> {
                item { SectionCard { MoneyRow(AppText.get(R.string.total_outstanding), BookMath.sum(outstanding.map { it.second }), true) }
                    if (outstanding.isEmpty()) EmptyState(AppText.get(R.string.all_caught_up), AppText.get(R.string.no_outstanding_rent_is_recorded)) }
                items(outstanding, key = { it.first.id }) { (tenancy, due) -> SectionCard {
                    ActionRow("${state.book.flat(tenancy)?.flatNumber} · ${state.book.tenant(tenancy)?.full_name}", { onTenant(tenancy.id) }, R.drawable.ic_person, if (tenancy.status == "active") AppText.get(R.string.current_tenant) else AppText.get(R.string.previous_tenant))
                    MoneyRow(AppText.get(R.string.rent_due), due, true)
                } }
            }
            "Flat ledger" -> state.book.flats.sortedWith(compareBy({ it.floor }, { it.flatNumber })).forEach { flat ->
                item(key = "flat/${flat.id}") { SectionHeading(flat.flatNumber) }
                val history = state.book.tenancies.filter { it.flat_id == flat.id }.sortedByDescending { it.move_in_date }
                if (history.isEmpty()) item { SectionCard { Text(AppText.get(R.string.no_tenancy_history)) } }
                items(history, key = { "tenancy/${it.id}" }) { tenancy -> SectionCard {
                    ActionRow(state.book.tenant(tenancy)?.full_name.orEmpty(), { onTenant(tenancy.id) }, R.drawable.ic_person, if (tenancy.status == "active") AppText.get(R.string.current_tenant) else AppText.get(R.string.previous_tenant))
                    MoneyRow(AppText.get(R.string.rent_due), BookMath.due(state.book, tenancy.id))
                    MoneyRow(AppText.get(R.string.available_credit), BookMath.credit(state.book, tenancy.id))
                } }
            }
        }
        item { SectionCard(AppText.get(R.string.export)) {
            ActionRow(AppText.get(R.string.building_ledger_csv), { onExport("csv") }, R.drawable.ic_download)
            if (mode == "Monthly" || mode == "Annual") ActionRow(AppText.get(R.string.value_financial_pdf, appLabel(mode)), { onExport(if (mode == "Annual") "annual:${month.year}" else "pdf:$month") }, R.drawable.ic_document)
        } }
    }
}

@Composable private fun MonthlyReport(book: Book, month: YearMonth) {
    val summary = remember(book, month) { BookMath.summary(book, month) }
    SectionCard(AppText.get(R.string.monthly_position)) {
        MoneyRow(AppText.get(R.string.rent_cash_received), summary.collected, true)
        MoneyRow(AppText.get(R.string.expected_rent), summary.expected)
        MoneyRow(AppText.get(R.string.allocated_to_this_month), summary.allocated)
        MoneyRow(AppText.get(R.string.current_month_due), summary.currentDue)
        MoneyRow(AppText.get(R.string.earlier_outstanding), summary.oldDue)
        MoneyRow(AppText.get(R.string.paid_expenses), summary.expenses)
        HorizontalDivider()
        MoneyRow(AppText.get(R.string.net_cash), summary.net, true)
    }
    val paymentIds = book.payments.filter { !it.voided && it.kind == "cash" && it.payment_date.startsWith(month.toString()) }.map { it.id }.toSet()
    val oldPeriods = book.rent_periods.filter { it.period_date.take(7) < month.toString() }.map { it.id }.toSet()
    val recovery = BookMath.sum(book.payment_allocations.filter { it.payment_id in paymentIds && it.rent_period_id in oldPeriods }.map { it.amount_paisa })
    SectionCard { MoneyRow(AppText.get(R.string.earlier_dues_recovered), recovery) }
    CategoryExpenses(book.expenses.filter { !it.voided && it.expense_date.startsWith(month.toString()) })
    Text(AppText.get(R.string.deposits_and_brought_forward_credit_are_excluded_from_cash_rent_income))
}

@Composable private fun AnnualReport(book: Book, year: Int) {
    val months = remember(book, year) { (1..12).map { BookMath.summary(book, YearMonth.of(year, it)) } }
    SectionCard(AppText.get(R.string.year_value, year)) {
    MoneyRow(AppText.get(R.string.expected_rent), BookMath.sum(months.map { it.expected }))
    MoneyRow(AppText.get(R.string.rent_cash_received), BookMath.sum(months.map { it.collected }), true)
    MoneyRow(AppText.get(R.string.paid_expenses), BookMath.sum(months.map { it.expenses }))
    MoneyRow(AppText.get(R.string.net_cash), BookMath.sum(months.map { it.net }), true)
    }
    months.forEachIndexed { index, summary ->
        SectionCard(YearMonth.of(year, index + 1).month.getDisplayName(java.time.format.TextStyle.FULL, DisplayLocale.locale)) {
            MoneyRow(AppText.get(R.string.rent_cash), summary.collected)
            MoneyRow(AppText.get(R.string.expenses), summary.expenses)
            MoneyRow(AppText.get(R.string.net_cash), summary.net, true)
        }
    }
    CategoryExpenses(book.expenses.filter { !it.voided && it.expense_date.startsWith("$year-") })
}

@Composable private fun CategoryExpenses(expenses: List<Expense>) {
    SectionCard(AppText.get(R.string.expenses_by_category)) {
    if (expenses.isEmpty()) Text(AppText.get(R.string.no_paid_expenses_in_this_period))
    expenses.groupBy { it.category }.toSortedMap().forEach { (category, entries) ->
        MoneyRow(appLabel(category), BookMath.sum(entries.map { it.amount_paisa }))
    }
    }
}
