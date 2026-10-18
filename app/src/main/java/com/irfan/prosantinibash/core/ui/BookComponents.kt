package com.irfan.prosantinibash.core.ui

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.irfan.prosantinibash.core.util.Money

@Composable fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}
@Composable fun MoneyRow(label: String, amount: Long, emphasis: Boolean = false) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
        Text(Money.format(amount), style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    }
}
@Composable fun StatusBadge(label: String) {
    val colors = MaterialTheme.colorScheme
    val status = listOf("paid", "completed", "active", "overdue", "voided", "due", "partial", "pending", "open")
        .firstOrNull { label.equals(it, true) || label == appLabel(it) } ?: label.lowercase()
    val pair = when (status) {
        "paid", "completed", "active" -> colors.primaryContainer to colors.onPrimaryContainer
        "overdue", "voided" -> colors.errorContainer to colors.onErrorContainer
        "due", "partial", "pending", "open" -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    Surface(color = pair.first, contentColor = pair.second, shape = MaterialTheme.shapes.small) {
        Text(appLabel(label), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun SectionHeading(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}
@Composable fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable fun ActionRow(label: String, onClick: () -> Unit, icon: Int? = null, supporting: String? = null) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        ListItem(headlineContent = { Text(label) }, supportingContent = supporting?.let { { Text(it) } },
            leadingContent = icon?.let { { Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.primary) } },
            trailingContent = { Icon(painterResource(R.drawable.ic_next), null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
    }
}
@Composable fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    SectionCard {
        Icon(painterResource(R.drawable.ic_info), null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actionLabel?.let { Button(onClick = onAction) { Text(it) } }
    }
}
/** Both system Back and the app bar dispatch through this guard. */
@Composable fun DraftGuard(dirty: Boolean, pending: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    BackHandler(enabled = (dirty || pending) && !leaving) { show = true }
    LaunchedEffect(leaving) { if (leaving) dispatcher?.onBackPressed() }
    if (show) AlertDialog(onDismissRequest = { show = false },
        title = { Text(if (pending) AppText.get(R.string.save_needs_attention) else AppText.get(R.string.discard_changes)) },
        text = { Text(if (pending) AppText.get(R.string.retry_the_original_save_before_leaving_this_form) else AppText.get(R.string.your_unsaved_changes_will_be_discarded)) },
        confirmButton = { if (!pending) TextButton(onClick = { show = false; leaving = true }) { Text(AppText.get(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { show = false }) { Text(AppText.get(R.string.keep_editing)) } })
}
