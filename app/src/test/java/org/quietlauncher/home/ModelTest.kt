// SPDX-License-Identifier: Apache-2.0
package org.quietlauncher.home

import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    private val clock = AppEntry("org.example.clock", "Clock")
    private val gallery = AppEntry("org.example.gallery", "Gallery")
    @Test fun defaultsAreEmptyAndNeutral() {
        assertEquals(listOf("Profile One", "Profile Two"), Defaults.profiles().map { it.name })
        assertTrue(Defaults.profiles().all { it.apps.isEmpty() })
        assertEquals("select-apps", HomeState().home().focusedId)
    }
    @Test fun chosenAppsKeepInsertionOrderWithoutDuplicates() {
        val state = HomeState().toggle(clock).toggle(gallery)
        assertEquals(listOf(clock, gallery), state.profile.apps)
        assertEquals(listOf(gallery), state.toggle(clock).profile.apps)
    }
    @Test fun selectionsStayWithinTheirProfile() {
        val state = HomeState().toggle(clock).selectProfile("profile_2").toggle(gallery)
        assertEquals(listOf(gallery), state.profile.apps)
        assertEquals(listOf(clock), state.selectProfile("profile_1").profile.apps)
    }
    @Test fun homeClosesPanelsClearsNoticeAndFocusesFirstApp() {
        val state = HomeState().toggle(clock).focus("settings").open(Panel.Apps).unavailable("Clock").home()
        assertEquals(Panel.Home, state.panel)
        assertEquals("app:org.example.clock", state.focusedId)
        assertNull(state.notice)
    }
    @Test fun backAtHomeIsNoOp() {
        val state = HomeState().focus("settings")
        assertEquals(state, state.back())
    }
    @Test fun backFromPanelRestoresOpener() {
        val state = HomeState().focus("profiles").open(Panel.Profiles).back()
        assertEquals(Panel.Home, state.panel)
        assertEquals("profiles", state.focusedId)
    }
    @Test fun switchingProfileResetsFocusAndClosesPanel() {
        val state = HomeState().toggle(clock).selectProfile("profile_2").toggle(gallery)
            .open(Panel.Profiles).selectProfile("profile_1")
        assertEquals(Panel.Home, state.panel)
        assertEquals("app:org.example.clock", state.focusedId)
    }
    @Test fun unknownProfileAndFocusAreIgnored() {
        assertEquals(HomeState(), HomeState().selectProfile("missing").focus("missing"))
    }
    @Test fun missingAppStaysVisibleAndRemovable() {
        val state = HomeState().toggle(gallery).unavailable("Gallery")
        assertTrue(state.notice!!.contains("Gallery"))
        assertEquals(listOf(gallery), pickerApps(state.profile.apps, emptyList()))
        assertTrue(state.toggle(gallery).profile.apps.isEmpty())
    }
    @Test fun discoveryMergesBothCategoriesAndExcludesSelf() {
        val own = AppEntry("org.quietlauncher.home", "Home")
        assertEquals(listOf(clock, gallery), appCatalog(listOf(gallery, clock, own), listOf(clock), own.packageId))
    }
    @Test fun emptyDiscoveryAndPickerAreSafe() {
        assertTrue(appCatalog(emptyList(), emptyList(), "org.quietlauncher.home").isEmpty())
        assertTrue(pickerApps(emptyList(), emptyList()).isEmpty())
    }
    @Test fun removingFocusedAppLeavesValidFocus() {
        val state = HomeState().toggle(clock).focus("app:org.example.clock").toggle(clock)
        assertEquals("select-apps", state.focusedId)
    }
}
