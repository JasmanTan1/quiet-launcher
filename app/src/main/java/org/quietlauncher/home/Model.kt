// SPDX-License-Identifier: Apache-2.0
package org.quietlauncher.home

/** Saved selections retain labels so an uninstalled app remains removable. */
data class AppEntry(val packageId: String, val label: String)
data class Profile(val id: String, val name: String, val apps: List<AppEntry> = emptyList())
enum class Panel { Home, Apps, Profiles }

object Defaults {
    const val STORAGE = "quiet-launcher.v1.preferences"
    fun profiles() = listOf(Profile("profile_1", "Profile One"), Profile("profile_2", "Profile Two"))
}

/** Pure navigation rules shared by the UI and offline checks. */
data class HomeState(
    val profiles: List<Profile> = Defaults.profiles(),
    val activeId: String = "profile_1",
    val panel: Panel = Panel.Home,
    val focusedId: String = "select-apps",
    val notice: String? = null,
) {
    val profile: Profile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    val targets: List<String> get() = profile.apps.map { "app:${it.packageId}" } + listOf("select-apps", "profiles", "settings")
    val firstTarget: String get() = targets.first()
    fun focus(id: String): HomeState = if (id in targets) copy(focusedId = id) else this
    fun home(): HomeState = copy(panel = Panel.Home, focusedId = firstTarget, notice = null)
    fun back(): HomeState = if (panel == Panel.Home) this else copy(panel = Panel.Home, notice = null)
    fun open(next: Panel): HomeState = copy(panel = next, notice = null)
    fun selectProfile(id: String): HomeState {
        if (profiles.none { it.id == id }) return this
        return copy(activeId = id, panel = Panel.Home, notice = null).home()
    }
    fun toggle(app: AppEntry): HomeState {
        val updated = if (profile.apps.any { it.packageId == app.packageId })
            profile.apps.filterNot { it.packageId == app.packageId }
        else profile.apps + app
        val result = copy(profiles = profiles.map { if (it.id == profile.id) it.copy(apps = updated) else it })
        return if (result.focusedId in result.targets) result else result.copy(focusedId = result.firstTarget)
    }
    fun unavailable(label: String): HomeState = copy(notice = "$label is unavailable. Select apps to remove it.")
}

/** Merge both launcher categories, prefer TV results, never add a preset app. */
fun appCatalog(tv: List<AppEntry>, standard: List<AppEntry>, ownPackage: String): List<AppEntry> =
    (tv + standard).filter { it.packageId != ownPackage }.distinctBy { it.packageId }
        .sortedWith(compareBy<AppEntry> { it.label.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageId })

fun pickerApps(selected: List<AppEntry>, installed: List<AppEntry>): List<AppEntry> =
    (selected + installed).distinctBy { it.packageId }
