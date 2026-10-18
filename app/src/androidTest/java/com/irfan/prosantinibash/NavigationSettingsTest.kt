package com.irfan.prosantinibash

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchesTabsAndKeepsPreferences() {
        compose.unlockTestApp()
        listOf("Flats", "Payments", "Expenses", "More").forEach { label ->
            compose.onAllNodesWithText(label).onLast().performClick()
        }
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithText("Dark").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.Selected]
        }
        if (Build.VERSION.SDK_INT >= 31) {
            compose.onNodeWithText("Dynamic color").performScrollTo()
            val switch = compose.onAllNodes(isToggleable()).onFirst()
            switch.performClick()
            compose.waitUntil(5_000) {
                switch.fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] ==
                    androidx.compose.ui.state.ToggleableState.On
            }
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Dark").assertIsSelected()
        if (Build.VERSION.SDK_INT >= 31) compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("Home").onLast().performClick()
        compose.onAllNodesWithText("Home").onLast().assertIsDisplayed()
    }
}

class LargeFontSettingsTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun timeoutOptionsFitAtDoubleFontSize() {
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as FoundationTestApplication
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                com.irfan.prosantinibash.core.design.ProsantiTheme(
                    com.irfan.prosantinibash.core.settings.AppPreferences()
                ) {
                    com.irfan.prosantinibash.feature.settings.SettingsScreen(
                        app.container.preferences, com.irfan.prosantinibash.core.settings.AppPreferences(),
                        onLock = {}, onChangePin = { old, new -> old.fill('\u0000'); new.fill('\u0000') },
                        onEnableBiometric = { it.fill('\u0000') }, onDisableBiometric = {},
                        biometricAvailable = false, securityError = null)
                }
            }
        }
        compose.onNodeWithText("15 min").performScrollTo().assertIsDisplayed()
        val option = compose.onNodeWithText("15 min").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Auto-lock option must remain on screen at 200% fonts", option.right <= root.right)
    }
}
