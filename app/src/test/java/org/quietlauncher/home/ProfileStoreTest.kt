// SPDX-License-Identifier: Apache-2.0
package org.quietlauncher.home

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileStoreTest {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var store: ProfileStore
    @Before fun setup() {
        prefs = RuntimeEnvironment.getApplication<Application>().getSharedPreferences("test", 0)
        prefs.edit().clear().commit()
        store = ProfileStore(prefs)
    }
    @Test fun roundTripPreservesProfilesAndMissingSelections() {
        val state = HomeState().toggle(AppEntry("org.example.gallery", "Gallery")).selectProfile("profile_2")
        store.save(state)
        assertEquals(state, store.load())
    }
    @Test fun malformedDataResetsSafely() {
        prefs.edit().putString("selections", "broken").apply()
        assertEquals(HomeState().home(), store.load())
    }
    @Test fun invalidSavedProfileFallsBackToFirst() {
        prefs.edit().putString("activeProfile", "missing").apply()
        assertEquals("profile_1", store.load().profile.id)
    }
    @Test fun invalidAppEntriesAreRejectedAndDuplicatesRemoved() {
        prefs.edit().putString("selections", """{"profile_1":[{"packageId":"bad value","label":"Bad"},{"packageId":"org.example.clock","label":"Clock"},{"packageId":"org.example.clock","label":"Duplicate"}]}""").apply()
        assertEquals(listOf(AppEntry("org.example.clock", "Clock")), store.load().profile.apps)
    }
}
