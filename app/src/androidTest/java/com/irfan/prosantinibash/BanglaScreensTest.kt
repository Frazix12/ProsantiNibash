package com.irfan.prosantinibash

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.core.export.Exports
import com.irfan.prosantinibash.core.i18n.*
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.core.util.BuildingDates
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.feature.book.*
import com.irfan.prosantinibash.feature.home.HomeScreen
import com.irfan.prosantinibash.feature.expenses.*
import java.time.YearMonth
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BanglaScreensTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun bangla() { AppText.configure(context, "bn") }
    @After fun english() { AppText.configure(context, "en") }

    @Test fun translatedExpenseSearchAndFiltersKeepCanonicalValues() {
        val date = "${YearMonth.now(BuildingDates.zone)}-01"
        val book = Book(expenses = listOf(Expense("a", 10000, date, "Water", "Water bill"),
            Expense("b", 20000, date, "Repair", "Door repair")))
        val model = BookViewModel({ book }, { _, _, _ -> })
        compose.setContent { ProsantiTheme(AppPreferences(language = "bn")) { Surface {
            ExpensesScreen(BookUiState(book = book, loaded = true), model, {}, {})
        } } }
        compose.onNodeWithText("৳৩০০.০০").assertIsDisplayed()
        compose.onNodeWithText("খরচ খুঁজুন").performScrollTo().performTextInput("পানি")
        compose.onNodeWithText("Water bill").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Door repair").assertDoesNotExist()
        compose.onNodeWithText("ফিল্টার মুছুন").performScrollTo().performClick()
        compose.onNodeWithText("Door repair").performScrollTo().assertIsDisplayed()
        capture("expenses-bangla")
        assertEquals("Water", book.expenses.first().category)
    }

    @Test fun homeDatesAndBreakdownAreTranslated() {
        val book = Book(flats = listOf(Flat("flat", 1, "1A", 10000)))
        val model = BookViewModel({ book }, { _, _, _ -> })
        compose.setContent { ProsantiTheme(AppPreferences(language = "bn")) { Surface {
            HomeScreen(BookUiState(book = book, loaded = true), model, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("মোট বকেয়া").assertIsDisplayed()
        compose.onNodeWithText("বিস্তারিত দেখুন").performClick()
        compose.onNodeWithText("ধার্য ভাড়া").assertIsDisplayed()
        capture("home-bangla")
    }

    @Test fun pdfSupportsBanglaAndCsvKeepsMachineFormat() = runBlocking {
        val month = YearMonth.now(BuildingDates.zone)
        val book = Book(building = Building(name = "প্রশান্তি নিবাস"), expenses = listOf(
            Expense("a", 123456, "$month-01", "Water", "পানির বিল")))
        val pdf = Exports.report(context, book, "annual:${month.year}")
        pdf.copyTo(File(context.externalCacheDir, "bangla-report.pdf"), overwrite = true)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.externalCacheDir, "bangla-report.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
        val csv = Exports.report(context, book, "csv").readText()
        assertTrue(csv.contains("amount_paisa"))
        assertTrue(csv.contains("123456"))
        assertTrue(csv.contains("$month-01"))
        assertEquals("বকেয়া ভাড়ার দিকে নজর দিন", appLabel("Outstanding rent needs attention"))
    }

    @Test fun auditTranslatesMetadataAndPreservesUserText() {
        val data = kotlinx.serialization.json.buildJsonObject {
            put("payment_method", kotlinx.serialization.json.JsonPrimitive("Cash"))
            put("amount_paisa", kotlinx.serialization.json.JsonPrimitive(12345))
            put("title", kotlinx.serialization.json.JsonPrimitive("Cash"))
        }
        val text = com.irfan.prosantinibash.feature.reports.auditValue("record", data)
        assertTrue(text.contains("পরিশোধের মাধ্যম: নগদ"))
        assertTrue(text.contains("পরিমাণ (পয়সা): ১২৩৪৫"))
        assertTrue(text.contains("শিরোনাম: Cash"))
    }

    private fun capture(name: String) {
        File(context.externalCacheDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
