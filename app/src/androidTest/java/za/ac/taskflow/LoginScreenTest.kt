package za.ac.taskflow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class LoginScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun invalidEmailShowsHelpfulErrorWithoutSendingRequest() {
        compose.onNodeWithText("Email address").performTextInput("not-an-email")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("validpassword")
        compose.onNodeWithText("Sign in",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Enter a valid email address.").performScrollTo().assertIsDisplayed()
    }
}
