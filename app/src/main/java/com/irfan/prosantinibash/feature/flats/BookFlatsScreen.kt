package com.irfan.prosantinibash.feature.flats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.irfan.prosantinibash.R
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.data.model.Flat
import com.irfan.prosantinibash.feature.book.BookUiState
import com.irfan.prosantinibash.feature.book.BookViewModel
import java.time.YearMonth

@Composable
fun BookFlatsScreen(state: BookUiState, model: BookViewModel, onFlat: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val month = YearMonth.now(BuildingDates.zone)
    val flats = state.book.flats.filter { flat ->
        val tenant = state.book.active(flat.id)?.let(state.book::tenant)
        "${flat.flatNumber} ${tenant?.full_name.orEmpty()} ${tenant?.phone.orEmpty()}".contains(query, true) &&
            (filter == "All" || flatStatus(state.book, flat, month) == filter)
    }
    BookListPage(state, model::refresh) {
        item { TextEntry(AppText.get(R.string.search_flat_tenant_or_phone), query) { query = it } }
        item { ChoiceEntry(AppText.get(R.string.status), listOf("All", "Paid", "Partial", "Due", "Overdue", "Vacant", "No charge"), filter) { filter = it } }
        if (flats.isEmpty()) item { EmptyState(AppText.get(R.string.no_matching_flats), AppText.get(R.string.try_another_name_flat_number_or_status)) }
        floorTiles(state.book, flats, month, onFlat)
    }
}

private fun LazyListScope.floorTiles(book: Book, flats: List<Flat>, month: YearMonth, onFlat: (String) -> Unit) {
    flats.groupBy { it.floor }.toSortedMap().forEach { (floor, rows) ->
        item(key = "floor-$floor") { SectionHeading(AppText.get(R.string.floor_value, floor)) }
        item(key = "floor-tiles-$floor") { FloorTiles(book, rows.sortedBy { it.flatNumber }, month, onFlat) }
    }
}

@Composable
private fun FloorTiles(book: Book, flats: List<Flat>, month: YearMonth, onFlat: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                flats.forEach { FlatTile(book, it, month, Modifier.fillMaxWidth()) { onFlat(it.id) } }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            flats.forEach { FlatTile(book, it, month, Modifier.weight(1f)) { onFlat(it.id) } }
            if (flats.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlatTile(book: Book, flat: Flat, month: YearMonth, modifier: Modifier, onClick: () -> Unit) {
    val tenancy = book.active(flat.id)
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(flat.flatNumber, style = MaterialTheme.typography.titleLarge)
            StatusBadge(flatStatus(book, flat, month))
            tenancy?.let {
                Text(book.tenant(it)?.full_name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                val due = BookMath.due(book, it.id)
                if (due > 0) Text(AppText.get(R.string.value_outstanding, Money.format(due)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
