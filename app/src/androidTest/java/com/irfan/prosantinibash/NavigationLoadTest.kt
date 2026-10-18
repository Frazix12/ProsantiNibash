package com.irfan.prosantinibash

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.navigation.mainRoutes
import org.junit.Rule
import org.junit.Test

/** Uses the real route builder: delayed loads and refreshes must update destinations. */
class NavigationLoadTest {
    @get:Rule val compose = createComposeRule()
    @Test fun destinationObservesBookLoadedAfterGraphCreationAndLaterRefresh() {
        val state = mutableStateOf(BookUiState())
        val model = BookViewModel({ Book() }, { _, _, _ -> })
        val flat = Flat("flat", 1, "1A", 100000L)
        compose.setContent {
            val nav = rememberNavController()
            ProsantiTheme(AppPreferences()) { NavHost(nav, "home") { mainRoutes(state, model, nav) } }
        }
        compose.onNodeWithText("Loading your building records…").assertIsDisplayed()
        compose.runOnIdle { state.value = BookUiState(book = Book(flats = listOf(flat)), loaded = true) }
        compose.onNodeWithText("Rent cash collected").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Flat 1A"))
        compose.onNodeWithText("Flat 1A").assertIsDisplayed()
        val tenancy = Tenancy("tenancy", "flat", "tenant", 100000L, "2026-01-01", "2026-01-01", status = "active")
        compose.runOnIdle { state.value = state.value.copy(book = state.value.book.copy(
            tenants = listOf(Tenant("tenant", "Updated Tenant")), tenancies = listOf(tenancy))) }
        compose.onNode(hasText("Updated Tenant", substring = true)).assertIsDisplayed()
        compose.onAllNodes(hasText("Vacant", substring = true)).assertCountEquals(0)
    }
}
