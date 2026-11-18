// SPDX-License-Identifier: Apache-2.0
// Standalone UI replaces the fixed service shelf and private catalog.
package org.quietlauncher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFFEAF2FF)
private val Accent = Color(0xFF7DD8FF)

@Composable
fun HomeScreen(
    state: HomeState,
    installed: List<AppEntry>,
    resetKey: Int = 0,
    onState: (HomeState) -> Unit,
    onLaunch: (String) -> LaunchResult,
    onSettings: () -> LaunchResult,
) {
    BackHandler { onState(state.back()) }
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1226))) {
            Canvas(Modifier.fillMaxSize()) { drawBackdrop(Accent, true, 40.dp.toPx()) }
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                Text("Quiet Launcher", color = Ink, fontSize = 28.sp)
                Text(state.profile.name, color = Accent, fontSize = 18.sp)
                Spacer(Modifier.height(20.dp))
                when (state.panel) {
                    Panel.Home -> HomeTiles(state, installed, resetKey, onState, onLaunch, onSettings, Modifier.weight(1f))
                    Panel.Apps -> AppPicker(state, installed, onState, Modifier.weight(1f))
                    Panel.Profiles -> ProfilePicker(state, onState, Modifier.weight(1f))
                }
                Text(state.notice ?: "D-pad to move · Select to choose · Back to close panels", color = Ink,
                    fontSize = 16.sp, modifier = Modifier.fillMaxWidth().testTag(if (state.notice != null) "notice" else "help"))
            }
        }
    }
}

@Composable
private fun HomeTiles(
    state: HomeState,
    installed: List<AppEntry>,
    resetKey: Int,
    onState: (HomeState) -> Unit,
    onLaunch: (String) -> LaunchResult,
    onSettings: () -> LaunchResult,
    modifier: Modifier,
) {
    val targets = state.targets
    val requesters = remember(targets) { targets.associateWith { FocusRequester() } }
    val grid = rememberLazyGridState()
    val available = installed.map { it.packageId }.toSet()
    LaunchedEffect(state.activeId, targets, resetKey) {
        val target = state.focusedId.takeIf { it in targets } ?: state.firstTarget
        grid.scrollToItem(targets.indexOf(target))
        withFrameNanos { }
        requesters.getValue(target).requestFocus()
    }
    Column(modifier) {
        if (state.profile.apps.isEmpty()) {
            Text("Choose the apps for this profile.", color = Ink, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(targets, key = { it }) { id ->
                val app = state.profile.apps.firstOrNull { "app:${it.packageId}" == id }
                val label = app?.let { if (it.packageId in available) it.label else "${it.label} · unavailable" }
                    ?: when (id) { "select-apps" -> "Select apps"; "profiles" -> "Profiles"; else -> "Settings" }
                Tile(label, id, Modifier.focusRequester(requesters.getValue(id))
                    .onFocusChanged { if (it.isFocused) onState(state.focus(id)) }) {
                    when (id) {
                        "select-apps" -> onState(state.open(Panel.Apps))
                        "profiles" -> onState(state.open(Panel.Profiles))
                        "settings" -> if (onSettings() == LaunchResult.Unavailable)
                            onState(state.copy(notice = "Settings is unavailable on this device."))
                        else -> if (app != null && onLaunch(app.packageId) == LaunchResult.Unavailable)
                            onState(state.unavailable(app.label))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPicker(state: HomeState, installed: List<AppEntry>, onState: (HomeState) -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    val selected = state.profile.apps.map { it.packageId }.toSet()
    val available = installed.map { it.packageId }.toSet()
    val apps = pickerApps(state.profile.apps, installed)
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    Column(modifier.testTag("app-picker")) {
        Text("Select apps · ${state.profile.name}", color = Ink, fontSize = 22.sp)
        Text("Select a tile to add or remove it. Newly added apps appear last on Home.", color = Ink, fontSize = 16.sp)
        Tile("Done", "picker-done", Modifier.width(200.dp).padding(vertical = 12.dp).focusRequester(first)) { onState(state.back()) }
        if (apps.isEmpty()) Text("No launchable apps are visible. Settings remains available on Home.", color = Ink, fontSize = 18.sp)
        LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(apps, key = { it.packageId }) { app ->
                val label = (if (app.packageId in selected) "✓ " else "+ ") + app.label +
                    (if (app.packageId !in available) " · unavailable" else "")
                Tile(label, "pick:${app.packageId}") { onState(state.toggle(app)) }
            }
        }
    }
}

@Composable
private fun ProfilePicker(state: HomeState, onState: (HomeState) -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    Column(modifier.testTag("profile-picker"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Profiles", color = Ink, fontSize = 22.sp)
        state.profiles.forEachIndexed { index, profile ->
            Tile(profile.name + if (profile.id == state.profile.id) " · selected" else "", "profile:${profile.id}",
                Modifier.width(340.dp).then(if (index == 0) Modifier.focusRequester(first) else Modifier)) {
                onState(state.selectProfile(profile.id))
            }
        }
        Tile("Cancel", "profiles-cancel", Modifier.width(200.dp)) { onState(state.back()) }
    }
}

@Composable
private fun Tile(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().heightIn(min = 84.dp).testTag(tag).onFocusChanged { focused = it.isFocused }
        .background(if (focused) Color(0xFF305A78) else Color(0xFF142C44), shape)
        .border(if (focused) 3.dp else 1.dp, if (focused) Accent else Color(0xFF43627B), shape)
        .clickable(role = Role.Button, onClick = onClick).padding(18.dp)) {
        Text(label, color = Ink, fontSize = 19.sp, maxLines = 3)
    }
}
