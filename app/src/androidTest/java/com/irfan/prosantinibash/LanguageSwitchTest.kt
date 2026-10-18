package com.irfan.prosantinibash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the runner's isolated settings and PIN; no cloud records are modified. */
class LanguageSwitchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageSwitchIsImmediateAndSurvivesRecreation() {
        compose.unlockTestApp()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("বাংলা").performClick()
        waitFor("দেখতে কেমন হবে")
        compose.onNodeWithText("অ্যাপ লক করুন").performScrollTo().assertIsDisplayed()
        assertEquals("bn", (compose.activity.application as ProsantiApplication).container.preferences.state.value.preferences.language)
        assertEquals("৳১২৩.৪৫", com.irfan.prosantinibash.core.util.Money.format(12345))
        compose.activityRule.scenario.recreate()
        waitFor("দেখতে কেমন হবে")
        compose.onNodeWithText("English").performScrollTo().performClick()
        waitFor("Appearance")
        assertEquals("en", (compose.activity.application as ProsantiApplication).container.preferences.state.value.preferences.language)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
}
