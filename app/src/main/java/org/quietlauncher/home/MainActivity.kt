// SPDX-License-Identifier: Apache-2.0
// Lifecycle and Home reset adapted for independent local profile state.
package org.quietlauncher.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(HomeState())
    private var installed by mutableStateOf(emptyList<AppEntry>())
    private var resetKey by mutableIntStateOf(0)
    private lateinit var store: ProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(getSharedPreferences(Defaults.STORAGE, MODE_PRIVATE))
        state = store.load()
        setContent {
            HomeScreen(state, installed, resetKey, onState = { next ->
                if (next.profiles != state.profiles || next.activeId != state.activeId) store.save(next)
                state = next
            }, onLaunch = { Launcher.byPackage(this, it) }, onSettings = { Launcher.settings(this) })
        }
    }
    override fun onResume() {
        super.onResume()
        installed = Launcher.installedApps(this)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            state = state.home()
            resetKey++
        }
    }
}
