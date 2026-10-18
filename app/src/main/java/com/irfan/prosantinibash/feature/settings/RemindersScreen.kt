package com.irfan.prosantinibash.feature.settings

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.irfan.prosantinibash.core.settings.*
import com.irfan.prosantinibash.core.ui.SectionCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun RemindersScreen(repository:PreferencesRepository,preferences:AppPreferences) {
    val scope=rememberCoroutineScope();var error by remember { mutableStateOf<String?>(null) }
    fun save(enabled:Boolean) { scope.launch {
        try { repository.setRemindersEnabled(enabled);error=null }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { error=AppText.get(R.string.reminder_settings_could_not_be_saved_try_again) }
    } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) save(true) else error=AppText.get(R.string.notification_permission_was_denied_enable_it_in_android_app_settings_to_rec)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionCard(AppText.get(R.string.daily_reminders)) {
        Text(AppText.get(R.string.rent_overdue_balances_agreements_recurring_expenses_and_maintenance))
        Row { Text(AppText.get(R.string.reminders),Modifier.weight(1f));Switch(preferences.remindersEnabled,onCheckedChange={ enabled ->
            if(enabled && Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else save(enabled)
        }) }
        Text(AppText.get(R.string.around_9_00_am_bangladesh_time_android_may_delay_delivery_while_saving_batt),style=MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(appLabel(it), color=MaterialTheme.colorScheme.error) }
    }
}
