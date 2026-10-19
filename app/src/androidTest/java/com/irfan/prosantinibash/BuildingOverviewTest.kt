package com.irfan.prosantinibash

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.home.HomeScreen
import com.irfan.prosantinibash.feature.flats.BookFlatsScreen
import com.irfan.prosantinibash.feature.flats.FlatDetailsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Local-only fixtures exercise tiles without creating financial records in Supabase. */
class BuildingOverviewTest {
    @get:Rule val compose = createComposeRule()
    private val flats = (1..7).flatMap { floor -> listOf("A", "B").map {
        Flat("$floor$it", floor, "$floor$it", 100000L)
    } }

    @Test fun allFourteenFlatsRemainReachableAtLargeFont() {
        val book = Book(flats = flats)
        val model = BookViewModel({ book }, { _, _, _ -> })
        var selected = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                ProsantiTheme(AppPreferences()) {
                    BookFlatsScreen(BookUiState(book = book, loaded = true), model) { selected = it }
                }
            }
        }
        flats.forEach { flat ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(flat.flatNumber))
            compose.onNodeWithText(flat.flatNumber).performClick()
            compose.runOnIdle { assertEquals(flat.id, selected) }
        }
    }

    @Test fun occupiedFlatShowsTenantAndPaymentAction() {
        val tenancy = Tenancy("tenancy", "1A", "tenant", 100000L, "2026-01-01", "2026-01-01", status = "active")
        val book = Book(flats = flats, tenants = listOf(Tenant("tenant", "Fixture Owner")), tenancies = listOf(tenancy))
        val model = BookViewModel({ book }, { _, _, _ -> })
        var selected = ""
        compose.setContent {
            ProsantiTheme(AppPreferences()) {
                FlatDetailsScreen(BookUiState(book = book, loaded = true), model, "1A",
                    onTenant = {}, onCreate = {}, onPayment = { selected = it }, onMaintenance = {})
            }
        }
        compose.onNodeWithText("Fixture Owner").assertIsDisplayed()
        compose.onNodeWithText("Record payment").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("tenancy", selected) }
        compose.onAllNodesWithText("Vacant").assertCountEquals(0)
        captureFixture("flat-details")
    }
    @Test fun homeShowsUsefulEmptyBuildingAndActions() {
        val book = Book(flats = flats)
        val model = BookViewModel({ book }, { _, _, _ -> })
        compose.setContent {
            ProsantiTheme(AppPreferences()) {
                HomeScreen(BookUiState(book = book, loaded = true), model, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Rent cash collected").assertIsDisplayed()
        compose.onNodeWithText("Record payment").assertIsDisplayed()
        captureFixture("home")
    }
    private fun captureFixture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir, "ui-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
