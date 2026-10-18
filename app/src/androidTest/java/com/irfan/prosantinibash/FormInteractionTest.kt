package com.irfan.prosantinibash

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.core.settings.AppPreferences
import com.irfan.prosantinibash.core.ui.DateEntry
import com.irfan.prosantinibash.core.ui.DraftGuard
import org.junit.Rule
import org.junit.Test

/** UI fixtures never call the cloud repository. */
class FormInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyOptionalDateCanBeCleared() {
        compose.setContent {
            ProsantiTheme(AppPreferences()) {
                Column {
                    val optional = remember { mutableStateOf("2026-10-04") }
                    DateEntry("Paid date", "2026-10-04") {}
                    DateEntry("Reminder date", optional.value, optional = true) { optional.value = it }
                }
            }
        }
        compose.onNodeWithContentDescription("Clear Paid date").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear Reminder date").performClick()
        compose.onNodeWithContentDescription("Clear Reminder date").assertDoesNotExist()
        compose.onNodeWithText("Choose date").assertIsDisplayed()
    }
    @Test fun backKeepsDirtyFormUntilDiscardConfirmed() {
        var back: (() -> Unit)? = null
        compose.setContent {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            back = { dispatcher.onBackPressed() }
            ProsantiTheme(AppPreferences()) { DraftGuard(dirty = true) }
        }
        compose.runOnIdle { back!!() }
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
