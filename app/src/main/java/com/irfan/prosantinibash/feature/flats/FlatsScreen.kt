package com.irfan.prosantinibash.feature.flats

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Permanent floor-wise units; do not label payment or vacancy without tenancy data. */
@Composable
fun FlatsScreen(state: FlatsUiState, onRefresh: () -> Unit, configurationError: String? = null) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AppText.get(R.string.building_7_floors_14_flats), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh, enabled = !state.loading && configurationError == null) { Text(AppText.get(R.string.refresh)) }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        (configurationError ?: state.error)?.let { message -> item {
            Text(appLabel(message), color = MaterialTheme.colorScheme.error)
            if (configurationError == null) TextButton(onClick = onRefresh) { Text(AppText.get(R.string.try_again)) }
        } }
        if (!state.loading && state.error == null && configurationError == null && state.flats.isEmpty()) {
            item { Text(AppText.get(R.string.no_flats_configured)) }
        }
        state.flats.groupBy { it.floor }.forEach { (floor, flats) ->
            item(key = "floor-$floor") {
                Text(AppText.get(R.string.floor_value, floor), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            }
            items(flats, key = { it.id }) { flat ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(flat.flatNumber, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
