// SPDX-License-Identifier: Apache-2.0
// App launching and discovery adapted for generic launchable apps.
package org.quietlauncher.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

enum class LaunchResult { Ok, Unavailable }

object Launcher {
    fun installedApps(ctx: Context): List<AppEntry> {
        fun query(category: String) = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL,
        ).filter { it.activityInfo.enabled && it.activityInfo.exported && it.activityInfo.applicationInfo.enabled }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(ctx.packageManager).toString()) }
        return appCatalog(query(Intent.CATEGORY_LEANBACK_LAUNCHER), query(Intent.CATEGORY_LAUNCHER), ctx.packageName)
    }

    fun byPackage(ctx: Context, pkg: String): LaunchResult {
        val pm = ctx.packageManager
        // Resolve afresh: packages can disappear or become disabled after discovery.
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.Unavailable
        return start(ctx, intent)
    }
    fun settings(ctx: Context): LaunchResult = start(ctx, Intent(Settings.ACTION_SETTINGS))
    private fun start(ctx: Context, intent: Intent): LaunchResult = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        LaunchResult.Ok
    } catch (_: ActivityNotFoundException) {
        LaunchResult.Unavailable
    } catch (_: SecurityException) {
        LaunchResult.Unavailable
    }
}
