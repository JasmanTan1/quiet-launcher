// SPDX-License-Identifier: Apache-2.0
// Assertions adapted from the original D-pad tests using synthetic selections.
package org.quietlauncher.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusTest {
    @get:Rule val rule = createComposeRule()
    private val clock = AppEntry("org.example.clock", "Clock")
    private val gallery = AppEntry("org.example.gallery", "Gallery")
    private var state by mutableStateOf(HomeState())
    private var resetKey by mutableIntStateOf(0)
    private fun show(initial: HomeState = HomeState().toggle(clock).toggle(gallery).home(), installed: List<AppEntry> = listOf(clock)) {
        state = initial
        rule.setContent {
            HomeScreen(state, installed, resetKey, { state = it }, { LaunchResult.Unavailable }, { LaunchResult.Unavailable })
        }
        rule.waitForIdle()
    }
    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }
    @Test fun initialFocusUsesFirstSelection() { show(); rule.onNodeWithTag("app:org.example.clock").assertIsFocused() }
    @Test fun emptyProfileFocusesSelection() {
        show(HomeState().home(), emptyList()); rule.onNodeWithTag("select-apps").assertIsFocused()
    }
    @Test fun dpadMovesAcrossTilesAndReturns() {
        show(); press(Key.DirectionRight); rule.onNodeWithTag("app:org.example.gallery").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("app:org.example.clock").assertIsFocused()
    }
    @Test fun missingAppExplainsRecovery() {
        show(); press(Key.DirectionRight); press(Key.Enter)
        rule.onNodeWithTag("notice").assertTextContains("Gallery is unavailable", substring = true)
    }
    @Test fun pickerAddsUserSelection() {
        show(HomeState().home()); press(Key.Enter)
        rule.onNodeWithTag("app-picker").assertExists()
        rule.onNodeWithTag("pick:org.example.clock").performClick()
        rule.onNodeWithTag("picker-done").performClick()
        assertEquals(listOf(clock), state.profile.apps)
    }
    @Test fun missingSelectionCanBeRemoved() {
        show(); rule.onNodeWithTag("select-apps").performClick()
        rule.onNodeWithTag("pick:org.example.gallery").performClick()
        rule.onNodeWithTag("picker-done").performClick()
        rule.onNodeWithTag("app:org.example.gallery").assertDoesNotExist()
    }
    @Test fun switchingProfilesChangesSelectionsAndFocus() {
        show(); rule.onNodeWithTag("profiles").performClick()
        rule.onNodeWithTag("profile:profile_2").performClick()
        assertEquals("profile_2", state.profile.id)
        rule.onNodeWithTag("select-apps").assertIsFocused()
        rule.onNodeWithTag("app:org.example.clock").assertDoesNotExist()
    }
    @Test fun homeClosesPickerAndResetsFocus() {
        show(); rule.onNodeWithTag("select-apps").performClick()
        rule.runOnIdle { state = state.home(); resetKey++ }
        rule.waitForIdle()
        rule.onNodeWithTag("app-picker").assertDoesNotExist()
        rule.onNodeWithTag("app:org.example.clock").assertIsFocused()
    }
    @Test fun backClosesPanelThenStaysAtHome() {
        show(); rule.onNodeWithTag("select-apps").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }.performClick()
        // Exercise the same Back dispatcher used by the activity without launching a device.
        val activity = (rule as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>).activity as ComponentActivity
        rule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle(); assertEquals(Panel.Home, state.panel)
        rule.onNodeWithTag("select-apps").assertIsFocused()
        rule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        assertFalse(activity.isFinishing)
    }
}
