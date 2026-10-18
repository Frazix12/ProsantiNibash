package com.irfan.prosantinibash

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.runner.AndroidJUnitRunner
import java.io.File
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Instrumentation cannot read, overwrite or clear the installed app's security/settings files. */
class FoundationTestApplication : ProsantiApplication() {
    private val runId = UUID.randomUUID().toString()
    override fun getFilesDir(): File = File(super.getFilesDir(), "foundation-test-$runId").apply { mkdirs() }
    override fun getNoBackupFilesDir(): File = File(super.getNoBackupFilesDir(), "foundation-test-$runId").apply { mkdirs() }
}

class FoundationTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application =
        super.newApplication(loader, FoundationTestApplication::class.java.name, context)
}

class LauncherTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensApp() {
        compose.onAllNodesWithText("Prosanti Nibash")[0].assertIsDisplayed()
    }
}

/** Tests use an isolated PIN; allow its full-strength verifier time on the two-core emulator. */
fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.unlockTestApp() {
    check(activity.application is FoundationTestApplication) { "UI tests require FoundationTestRunner." }
    waitUntil(10_000) { runCatching { onAllNodesWithText("PIN").fetchSemanticsNodes().isNotEmpty() ||
        onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    if (onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()) return
    val creating = onAllNodesWithText("Create a 4 or 6 digit PIN").fetchSemanticsNodes().isNotEmpty()
    onNodeWithText("PIN").performTextInput("2468")
    onNodeWithText(if (creating) "Continue" else "Unlock").performClick()
    if (creating) {
        waitUntil(60_000) { onAllNodesWithText("Confirm your PIN").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(60_000) { !onNodeWithText("PIN").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        onNodeWithText("PIN").performTextInput("2468")
        onNodeWithText("Continue").performClick()
    }
    waitUntil(60_000) { onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
}
