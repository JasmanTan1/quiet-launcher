# Quiet Launcher

An offline Android TV Home screen with D-pad controls, two local profiles and
user-selected applications. Each profile starts empty. Choose **Select apps**,
select tiles to add or remove them, then choose **Done**. Selection order becomes
Home order. **Profiles** switches between Profile One and Profile Two. Selections
and the active profile persist on this device.

App discovery combines TV and standard launcher categories, excludes this
launcher, and lists each package once. Standard Android apps may need touch or a
mouse and may not suit a TV remote. Selected apps remain visible if uninstalled,
disabled or no longer launchable; a failed launch displays a notice. Remove such
a selection through **Select apps**. Discovery refreshes when the launcher resumes.
There are no preset apps, links or remote services.

## Controls

- Use the D-pad to move between tiles and Select/Enter to activate them.
- Back closes app selection or profiles and restores Home focus. Back at Home
  keeps the launcher open.
- Home, when delivered to this launcher as an Android Home intent, closes panels
  and focuses the active profile's first app, or **Select apps** if empty.
- Settings opens the device's system Settings when the device provides it.

## Device limitations and recovery

Requires Android 12 / API 31 or later and a landscape TV environment. Vendors can
restrict default Home selection, remap remote buttons, hide activities or prevent
third-party launchers from receiving Home. A visible TV app entry does not
establish default Home status. This project does not replace vendor system apps
or alter Home button routing. Device behavior still needs verification.

Keep the original system launcher installed and enabled. Before changing the
default Home app, check that system Settings is accessible through the remote's
settings button or another system entry. If the device offers a default Home app
selector, use its Settings UI to choose Quiet Launcher. Keep a route back to that
selector. To recover, use system Settings to choose the original Home app; if
necessary clear Quiet Launcher's defaults or uninstall it from Settings. Clearing
its storage resets the two profiles and all selections. Vendor menu names vary;
if Home selection or a recovery route is unavailable, use this as an ordinary TV
app instead. No vendor packages need to be disabled.

## Build and verification

Open the repository root as the Gradle project. Required toolchain: JDK 17, Android SDK 34,
Gradle 8.9. Plugin and library versions are retained in the build files. Supply the
SDK using your own local configuration. No sibling repository, private signing
file, Git history, environment file or service is required. Release signing is
not configured. The project has no network permission.

The Gradle 8.9 wrapper is included with a pinned distribution checksum and
unchanged upstream script notices. From the repository root,
run `./gradlew --offline --no-daemon --max-workers=2 :app:testDebugUnitTest` for JUnit
and Robolectric checks, and `:app:recordPaparazziDebug` for synthetic captures.
Paparazzi verification requires reviewed baselines. Run `:app:lintDebug`,
`:app:assembleDebug` and `:app:assembleRelease` for lint, debug APK and unsigned
release APK. Offline builds require dependencies already provisioned; these
commands do not imply that a build has been verified. Set `ANDROID_HOME` for
the SDK. Release CI preflight also requires reviewed dependency locks and
verification metadata, which are pending for this project.
Run the frozen contract and fixture checks with
`python3 -I ci/check.py contract --product launcher --report /tmp/quiet-contract.json`. Provision the matching Gradle distribution before running the
wrapper on an offline build host; `--offline` controls dependency resolution
after the wrapper starts Gradle and does not prevent the wrapper's initial download.

See [the screenshot checklist](docs/SCREENSHOTS.md), [privacy](PRIVACY.md),
[third-party inventory](THIRD-PARTY.md) and [security](SECURITY.md).
Project-authored code and documents use Apache-2.0; see [LICENSE](LICENSE) and
[NOTICE](NOTICE). Android platform fonts and installed app labels remain supplied
by the user's device.
