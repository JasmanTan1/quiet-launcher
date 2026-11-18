// SPDX-License-Identifier: Apache-2.0
package org.quietlauncher.home

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun expose(category: String, pkg: String, label: String, enabled: Boolean = true) {
        val info = ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                exported = true
                this.enabled = enabled
                applicationInfo = ApplicationInfo().apply { packageName = pkg; this.enabled = true }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(category), info,
        )
    }
    @Test fun discoversBothIntentCategoriesAndDeduplicatesPackages() {
        expose(Intent.CATEGORY_LEANBACK_LAUNCHER, "org.example.clock", "Clock")
        expose(Intent.CATEGORY_LAUNCHER, "org.example.clock", "Clock")
        expose(Intent.CATEGORY_LAUNCHER, "org.example.gallery", "Gallery")
        assertEquals(listOf("org.example.clock", "org.example.gallery"), Launcher.installedApps(app).map { it.packageId })
    }
    @Test fun excludesSelfAndDisabledActivities() {
        expose(Intent.CATEGORY_LEANBACK_LAUNCHER, app.packageName, "Home")
        expose(Intent.CATEGORY_LAUNCHER, "org.example.gallery", "Gallery", enabled = false)
        assertTrue(Launcher.installedApps(app).none { it.packageId in setOf(app.packageName, "org.example.gallery") })
    }
    @Test fun absentPackageReturnsUnavailableWithoutOpeningAnything() {
        assertEquals(LaunchResult.Unavailable, Launcher.byPackage(app, "org.example.absent"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun launchBlockedBySystemHasRecoverableResult() {
        val blocked = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { throw SecurityException("synthetic denial") }
        }
        assertEquals(LaunchResult.Unavailable, Launcher.settings(blocked))
    }
    @Test fun settingsLaunchUsesSystemIntent() {
        val recording = object : ContextWrapper(app) {
            var opened: Intent? = null
            override fun startActivity(intent: Intent) { opened = intent }
        }
        assertEquals(LaunchResult.Ok, Launcher.settings(recording))
        assertEquals(android.provider.Settings.ACTION_SETTINGS, recording.opened?.action)
        assertTrue(recording.opened!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
