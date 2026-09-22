package za.ac.taskflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in integration test against the real API on the emulator host. Uses disposable data. */
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) {
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun enter(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
        hideKeyboard()
    }
    private fun click(text: String) {
        waitReady()
        compose.onNodeWithText(text, substring = false).performClick()
    }
    private fun hideKeyboard() {
        compose.runOnUiThread {
            val manager = compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }
    private fun scrollClick(text: String) {
        waitReady()
        compose.onNodeWithText(text, substring = false).performScrollTo().performClick()
    }
    private fun waitReady() {
        compose.waitUntil(60_000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun accountBoardTaskRewardsSettingsAndLogin() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("taskflowIntegration") == "true")
        val email = "device-${System.currentTimeMillis()}@example.com"
        val password = "TaskFlowTest-42!"
        scrollClick("New here? Create an account")
        enter("Full name", "Device Test")
        enter("Email address", email)
        enter("Password", password)
        enter("Confirm password", password)
        scrollClick("Create account")
        waitFor("Create a board")
        click("Create a board")
        // Dialog fields are not inside a scroll container.
        compose.onNode(hasSetTextAction() and hasText("Board name")).performTextInput("Runtime check")
        hideKeyboard()
        click("Save board")
        waitFor("Runtime check")
        compose.onNodeWithContentDescription("Add task").performClick()
        enter("Task title", "Verify TaskFlow")
        enter("Description", "Created by the running Android app")
        enter("Due date (optional)", "2020-01-01")
        enter("New checklist item", "Complete device test")
        compose.onNodeWithContentDescription("Add checklist item").performScrollTo().performClick()
        scrollClick("Save task")
        waitFor("Verify TaskFlow")
        click("Verify TaskFlow")
        waitFor("First completion earns 10 points.")
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.waitForIdle()
        scrollClick("Done")
        waitFor("MEDIUM priority • DONE")
        click("Close")
        click("Progress")
        waitFor("10")
        compose.onNodeWithText("Unlocked").assertExists()
        click("Settings")
        enter("Your name", "Device Test Updated")
        scrollClick("Dark")
        scrollClick("Save preferences")
        waitFor("Preferences saved.")
        scrollClick("Sign out")
        waitFor("Sign in")
        enter("Email address", email)
        enter("Password", password)
        scrollClick("Sign in")
        waitFor("Runtime check")
        click("Settings")
        compose.onNode(hasSetTextAction() and hasText("Your name")).assertTextContains("Device Test Updated")
        scrollClick("Sign out")
        waitFor("Sign in")
    }
}
