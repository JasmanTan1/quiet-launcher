// SPDX-License-Identifier: Apache-2.0
package org.quietlauncher.home

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local selections only. No accounts, network, device identifiers or migrated data. */
class ProfileStore(private val prefs: SharedPreferences) {
    fun load(): HomeState = try {
        val defaults = Defaults.profiles()
        val json = JSONObject(prefs.getString("selections", "{}") ?: "{}")
        val profiles = defaults.map { profile ->
            val apps = json.optJSONArray(profile.id) ?: JSONArray()
            profile.copy(apps = (0 until apps.length()).mapNotNull { index ->
                val item = apps.optJSONObject(index) ?: return@mapNotNull null
                val pkg = item.optString("packageId")
                val label = item.optString("label").take(120)
                if (pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) && label.isNotBlank())
                    AppEntry(pkg, label) else null
            }.distinctBy { it.packageId })
        }
        val active = prefs.getString("activeProfile", defaults.first().id)
        HomeState(profiles, profiles.firstOrNull { it.id == active }?.id ?: profiles.first().id).home()
    } catch (_: org.json.JSONException) {
        HomeState().home()
    } catch (_: ClassCastException) {
        HomeState().home()
    }

    fun save(state: HomeState) {
        val json = JSONObject()
        state.profiles.forEach { profile ->
            val apps = JSONArray()
            profile.apps.forEach { apps.put(JSONObject().put("packageId", it.packageId).put("label", it.label)) }
            json.put(profile.id, apps)
        }
        prefs.edit().putString("activeProfile", state.profile.id).putString("selections", json.toString()).apply()
    }
}
