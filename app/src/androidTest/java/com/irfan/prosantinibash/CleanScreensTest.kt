package com.irfan.prosantinibash

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.core.util.BuildingDates
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.home.HomeScreen
import com.irfan.prosantinibash.feature.expenses.ExpensesScreen
import java.time.YearMonth
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** These fixtures exercise only local UI state, never financial writes. */
class CleanScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeKeepsBreakdownAccessible() {
        val book = Book(flats = listOf(Flat("flat", 1, "1A", 10000)))
        val model = BookViewModel({ book }, { _, _, _ -> })
        compose.setContent {
            ProsantiTheme(AppPreferences()) { Surface { HomeScreen(BookUiState(book = book, loaded = true), model, {}, {}, {}, {}) } }
        }
        compose.onNodeWithText("Outstanding dues").assertIsDisplayed()
        capture("home-clean")
        compose.onNodeWithText("Expected rent").assertDoesNotExist()
        compose.onNodeWithText("Show breakdown").performClick()
        compose.onNodeWithText("Expected rent").assertIsDisplayed()
        compose.onNodeWithText("Hide breakdown").performClick()
        compose.onNodeWithText("Expected rent").assertDoesNotExist()
    }

    @Test fun expenseSearchKeepsMonthlyTotalAndEditAction() {
        val date = "${YearMonth.now(BuildingDates.zone)}-01"
        val book = Book(expenses = listOf(
            Expense("a", 10000, date, "Water", "Water bill"),
            Expense("b", 20000, date, "Repair", "Door repair")))
        val model = BookViewModel({ book }, { _, _, _ -> })
        var edited: String? = null
        compose.setContent {
            ProsantiTheme(AppPreferences()) { Surface { ExpensesScreen(BookUiState(book = book, loaded = true), model, { edited = it }, {}) } }
        }
        compose.onNodeWithText("Search expenses").performScrollTo().performTextInput("Water")
        compose.onNodeWithText("Water bill").performScrollTo().assertIsDisplayed()
        capture("expenses-clean")
        compose.onNodeWithText("Door repair").assertDoesNotExist()
        compose.onNodeWithText("Edit expense").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("a", edited) }
        compose.onNodeWithText("Total spent this month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("৳300.00").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performScrollTo().performClick()
        compose.onNodeWithText("Door repair").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
