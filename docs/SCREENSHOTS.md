# Screenshot checklist

Capture the real sanitized UI through the included Paparazzi tests after build
verification. No screenshots or baselines are included yet. Use a landscape
1920×1080 canvas with no device identifiers, system account UI or installed
personal apps. Test fixtures contain only Clock, Gallery, Profile One and
Profile Two. The synthetic Gallery selection is absent from installed apps.

| Capture | Required view | Acceptance check |
| --- | --- | --- |
| initial_home | Empty Profile One | Select apps visibly focused; onboarding text readable |
| focus_movement | Clock and Gallery selections | Focus ring visible on Gallery; labels fit safe area |
| app_selection | Selection panel | Selected Clock, Done, add/remove hint visible |
| missing_app | Unavailable Gallery | Unavailable label and recovery notice visible |
| generic_profiles | Profile panel | Both neutral profiles visible; first option focused |

After recording, inspect each full frame visually. Confirm text, focus contrast,
clipping, D-pad reachability and absence of personal or account data. Strip image
metadata and audit the resulting files before export; do not copy inherited
screenshots. No capture is an assertion of physical-device behavior.

On an authorized test device later, separately verify default Home support,
repeated Home, Back at Home, launch/return, unavailable apps, standard app remote
compatibility, profile persistence and recovery to the original launcher.
