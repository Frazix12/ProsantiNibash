package com.irfan.prosantinibash.feature.building

import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.feature.book.*
import kotlinx.serialization.json.*
import java.util.UUID
import androidx.compose.ui.text.input.KeyboardType

@Composable fun BuildingScreen(state: BookUiState, model: BookViewModel) {
    val building = state.book.building
    var name by rememberSaveable { mutableStateOf(building.name) }
    var address by rememberSaveable { mutableStateOf(building.address) }
    var owner by rememberSaveable { mutableStateOf(building.owner_name) }
    var phone by rememberSaveable { mutableStateOf(building.owner_phone) }
    var editRevision by rememberSaveable { mutableStateOf(building.revision) }
    var request by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    BookPage(state, model::refresh) {
        DraftGuard(!saved && (name != building.name || address != building.address || owner != building.owner_name || phone != building.owner_phone), state.pendingRequestId != null)
        SectionCard(AppText.get(R.string.building)) {
        TextEntry(AppText.get(R.string.building_name), name) { name = it; saved = false }; TextEntry(AppText.get(R.string.address), address) { address = it; saved = false }
        if (attempted && name.isBlank()) Text(AppText.get(R.string.building_name_is_required), color = MaterialTheme.colorScheme.error) }
        SectionCard(AppText.get(R.string.owner_contact)) {
        TextEntry(AppText.get(R.string.owner_name), owner) { owner = it; saved = false }; TextEntry(AppText.get(R.string.owner_phone), phone, keyboardType = KeyboardType.Phone) { phone = it; saved = false } }
        Text(AppText.get(R.string.these_details_appear_on_receipts_and_reports_the_14_flats_remain_permanent))
        SaveButton(state, AppText.get(R.string.save_building_details)) { attempted = true; if (name.isNotBlank()) model.save("building_save", buildJsonObject {
            put("name", name.trim()); put("address", address.trim()); put("owner_name", owner.trim()); put("owner_phone", phone.trim()); put("revision", editRevision)
        }, request) { saved = true; editRevision += 1; request = UUID.randomUUID().toString() } }
        if (saved) StatusBadge(AppText.get(R.string.building_details_saved))
    }
}
