# Third-party inventory

The build retains the following existing declarations. This inventory records
coordinates; a complete resolved dependency and packaged-notice audit is pending.

| Component | Retained version | Use |
| --- | --- | --- |
| Android Gradle plugin | 8.5.2 | Build |
| Kotlin Android and Compose compiler plugins | 2.0.20 | Build |
| Paparazzi | 1.3.5 | Screenshot tests |
| Compose BOM | 2024.09.03 | Runtime and test constraints |
| Activity Compose | 1.9.2 | Runtime |
| Compose foundation, UI, tooling preview | BOM managed | Runtime |
| Compose Material, material-icons-core | 1.7.2 | Runtime |
| Compose UI test JUnit4, test manifest | BOM managed | Tests/debug |
| JUnit | 4.13.2 | Tests |
| Robolectric | 4.13 | Tests |
| Gradle wrapper | 8.9 | Build launcher; Apache-2.0; upstream notices retained in scripts and JAR |

No font, inherited screenshot, app icon, poster image or other third-party artwork
is included. The source project's Outfit font is deliberately omitted: this
candidate uses the platform sans-serif font, which the OS supplies. The Outfit
license was reviewed as extraction input; its font is not covered by this
project's Apache license and is not shipped. The icon and banner are newly drawn
vector shapes. The backdrop is modified programmatic drawing code.

Paparazzi renders synthetic app names and profiles. Any future font or image must
have its own redistribution evidence and upstream notices before inclusion.
Resolved dependencies and APK contents still require a release audit; this list
is not a blanket license grant for dependency binaries.

The wrapper JAR retains its upstream `META-INF/LICENSE`. Its checksum and the
distribution ZIP checksum are pinned separately; inclusion of the wrapper does
not establish provenance for cached dependencies or the installed Android SDK.
