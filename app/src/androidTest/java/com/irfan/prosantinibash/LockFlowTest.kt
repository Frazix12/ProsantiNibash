package com.irfan.prosantinibash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class LockFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun manualLockRejectsWrongPinAndRetainsUnlockedRotation() {
        compose.unlockTestApp()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lock app"))
        compose.onNodeWithText("Lock app").performClick()
        compose.onNodeWithText("Enter your PIN").assertIsDisplayed()
        compose.onAllNodesWithText("Home").assertCountEquals(0)
        compose.onNodeWithText("PIN").performTextInput("0000")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Incorrect PIN.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enter your PIN").assertIsDisplayed()
        compose.onNodeWithText("PIN").performTextInput("2468")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onAllNodesWithText("Enter your PIN").assertCountEquals(0)
    }
}
