// SPDX-License-Identifier: Apache-2.0
// Synthetic screenshots replace all inherited captures.
package org.quietlauncher.home

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class HomeScreenshotTest {
    private val tv = DeviceConfig.NEXUS_5.copy(
        screenWidth = 1920, screenHeight = 1080, xdpi = 320, ydpi = 320,
        density = com.android.resources.Density.XHIGH, softButtons = false,
        orientation = com.android.resources.ScreenOrientation.LANDSCAPE,
    )
    @get:Rule val paparazzi = Paparazzi(deviceConfig = tv, theme = "android:Theme.Material.NoActionBar")
    private val clock = AppEntry("org.example.clock", "Clock")
    private val gallery = AppEntry("org.example.gallery", "Gallery")
    private fun snapshot(state: HomeState) = paparazzi.snapshot {
        HomeScreen(state, listOf(clock), onState = {}, onLaunch = { LaunchResult.Unavailable }, onSettings = { LaunchResult.Unavailable })
    }
    @Test fun initial_home() = snapshot(HomeState().home())
    @Test fun focus_movement() = snapshot(HomeState().toggle(clock).toggle(gallery).home().focus("app:org.example.gallery"))
    @Test fun app_selection() = snapshot(HomeState().toggle(clock).open(Panel.Apps))
    @Test fun missing_app() = snapshot(HomeState().toggle(gallery).home().unavailable("Gallery"))
    @Test fun generic_profiles() = snapshot(HomeState().open(Panel.Profiles))
}
